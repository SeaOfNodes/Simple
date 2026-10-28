package com.seaofnodes.simple.codegen;

import com.seaofnodes.graph.GraphObserver;
import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.io.File;
import java.util.*;


// Compile Driver for a Compilation Unit
@SuppressWarnings("unchecked")
public class CodeGen {

    // Last created CodeGen as a global; used all over to avoid passing about a "context".
    public static CodeGen CODE;

    // Location of built-in CPU ports
    public static final String PORTS = "com.seaofnodes.simple.node.cpus";

    // Compile phases
    public enum Phase {
        Parse,                  // Parse ASCII text into Sea-of-Nodes IR
        Iter,                   // Pessimistic peepholes after parse
        Opto,                   // SCCP, numeric modes, seed the peephole worklist
        IterPostOpto,           // Peepholes and inlining using SCCP results
        FinalizeIR,             // Field layout, offset cleanup, function/CU roots
        TypeCheck,              // Last check for bad programs
        Lower,                  // Expand checked class initialization to ordinary IR
        LoopTree,               // Build a loop tree; break infinite loops
        Select,                 // Convert to target hardware nodes
        Schedule,               // Global schedule (code motion) nodes
        LocalSched,             // Local schedule
        RegAlloc,               // Register allocation
        Encoding,               // Encoding
        Export,                 // Export
        LastPhase               // After last phase
    }
    public Phase _phase;
    // Null for normal compilation; owned by this compilation, not a global viewer.
    public GraphObserver<Node> _obs;

    /** True when tests deliberately randomize Iter worklist order. */
    public static boolean iterSeedOverridden() { return System.getProperty("simple.iter.seed") != null; }

    // Expensive assert sampling:
    //   -Dsimple.assert.expensive=0 disables these checks
    //   -Dsimple.assert.expensive=1 checks every step
    //   -Dsimple.assert.expensive=N checks once every 1<<N steps
    private static final int EXPENSIVE_ASSERT_LOG = Integer.getInteger("simple.assert.expensive",8);
    public static boolean expensiveAssert() { return EXPENSIVE_ASSERT_LOG > 0; }
    public static boolean expensiveAssert( int trip ) {
        if( EXPENSIVE_ASSERT_LOG <= 0 ) return false;
        if( EXPENSIVE_ASSERT_LOG == 1 ) return true;
        int shift = Math.min(EXPENSIVE_ASSERT_LOG,30);
        return (trip & ((1<<shift)-1)) == 0;
    }

    // ---------------------------
    // Module Source Root
    public final String _modDir;
    // Module Build Root
    public final String _buildDir;
    // Slashed path from source root to the entry file, without ".smp".
    public final String _srcName;

    // Only available for tests, otherwise source comes from the CompUnit
    private final String _src;

    // Current Working Directory; default module base
    private final String _cwd;

    // Source files discovered in this invocation. All live initializers
    // participate in cycle checking, including fully inlined bodies.
    public HashMap<String,CompUnit> _compunits;

    // Test shortcut for only one compilation unit
    public CompUnit compunit() {
        assert _compunits.size()==1;
        for( CompUnit cu : _compunits.values() )
            return cu;
        assert false : "no compilation unit";
        return null;
    }

    // True when fidx is published through a public class or instance field.
    public boolean publicFIDX(int fidx) {
        if( fidx < LocalIds.RESERVED ) return false;
        // A public class initializer is itself an external root; unlike
        // constructors and methods, it is not reached through a class field.
        FunNode fun = _linker.atX(fidx);
        if( fun != null && fun.isClz() && fun.isPublic() )
            return true;
        // Include instance methods even while their constant function fields
        // are stored in each instance rather than on the class object.
        for( Type t : Parser.TYPES.values() )
            if( t instanceof TypeStruct clz &&
                Parser.startsClzPrefix(clz._name) &&
                Parser.isPublicClassName(clz._name) &&
                resolvedClass(clz) ) {
                if( publicFieldFIDX(clz,fidx) ) return true;
                Type self = Parser.TYPES.get(clz._name.substring(Parser.CLZ.length()));
                if( self instanceof TypeStruct ts && publicFieldFIDX(ts,fidx) ) return true;
            }
        return false;
    }

    private static boolean publicFieldFIDX(TypeStruct clz, int fidx) {
        boolean hasCtor = clz.field("<ctor>") != null;
        for( Field fld : clz._fields )
            if( fld._fname.charAt(0)!='_' && fld._t instanceof TypeFunPtr tfp &&
                // With a user constructor, class:N.N is the private <init>.
                !(hasCtor && clz.hiddenInit(fld)) &&
                XInt.bit(tfp._fidxs,fidx) )
                return true;
        return false;
    }

    public static boolean hasUserConstructor(TypeStruct self) {
        Type t = Parser.TYPES.get(Parser.addClzPrefix(self._name));
        return t instanceof TypeStruct clz && clz.field("<ctor>") != null;
    }

    // A forward reference gets a class object and constructor signature so
    // parsing can proceed, but it has no callable definition yet.
    private static boolean resolvedClass(TypeStruct clz) {
        Type t = Parser.TYPES.get(clz._name.substring(Parser.CLZ.length()));
        return !(t instanceof TypeStruct ts) || !ts._fref;
    }

    private int[] _publicFIDXs, _publicAliases;

    /**
     * Freeze the externally visible portion of the program after ParseAll has
     * loaded every compilation unit and closed/upgraded all types.  Later
     * optimization can remove linker entries and create inline-only fidxs;
     * neither operation changes this interface.
     */
    void freezePublicInterface() {
        assert _publicFIDXs==null && _publicAliases==null;
        int[] fidxs = XInt.EMPTY;
        for( int fidx=LocalIds.RESERVED; fidx<_linker._len; fidx++ )
            if( publicFIDX(fidx) )
                fidxs = XInt.make(fidxs,fidx);
        int[] aliases = XInt.EMPTY;
        for( Type t : Parser.TYPES.values() )
            if( t instanceof TypeStruct clz &&
                Parser.startsClzPrefix(clz._name) &&
                Parser.isPublicClassName(clz._name) &&
                resolvedClass(clz) ) {
                boolean hasCtor = clz.field("<ctor>") != null;
                for( Field fld : clz._fields )
                    if( fld._fname.charAt(0)!='_' && fld._t!=Type.TOP &&
                        !(hasCtor && clz.hiddenInit(fld)) )
                        aliases = XInt.make(aliases,fld._alias);
            }
        _publicFIDXs = fidxs;
        _publicAliases = aliases;
    }

    public int[] publicFIDXs()  { assert _publicFIDXs !=null; return _publicFIDXs;  }
    public int[] publicAliases(){ assert _publicAliases!=null; return _publicAliases; }
    public boolean publicInterfaceFrozen() { return _publicFIDXs != null; }

    // ---------------------------

    // Very common nodes, cached here
    public final ConstantNode ZERO;
    public final XCtrlNode XCTRL;

    public Node constant( Type t ) {
        assert _phase==null || _phase.ordinal() <= Phase.Lower.ordinal();
        return ConstantNode.make(t).peephole();
    }
    public ConstantNode con( Type t ) { return (ConstantNode)constant(t); }
    public ConstantNode con( long con ) { return con==0 ? ZERO : con(TypeInteger.constant(con));  }


    // ---------------------------
    // Test setup; no module nor file with specific argument
    public CodeGen( String src ) { this(src, 126L, true ); }

    // Test setup; no module nor file; can alter seed & argument; can re-run same CodeGen
    public CodeGen( String src, Type arg ) {
        this(null,null,null, src, 126L, true, arg);
    }
    // Test setup; no module nor file; can alter seed & argument; can re-run same CodeGen
    public CodeGen( String src, long workListSeed, boolean resetTypes ) {
        this(null,null,null, src, workListSeed, resetTypes, TypeInteger.BOT);
    }

    // Generic CodeGen, including full module setup
    public CodeGen( String modDir, String buildDir,
                    String srcName, String src, long workListSeed, boolean resetTypes, Type arg ) {
        // Public singleton to avoid passing about this state to a huge count
        // of places.  Probably becomes a TLS at some point.
        CODE = this;
        if( resetTypes ) Type.reset();           // Reset to recover from failed test

        _cwd = System.getProperty("user.dir")+"/";
        _modDir   =   modDir == null ? _cwd :   modDir;
        _buildDir = buildDir == null ? _cwd : buildDir;
        _srcName = srcName;
        _callingConv = null;       // Calling convention
        // Source code from test strings, not files
        _src = src;
        // All the compilation units
        _compunits = new HashMap<>();
        _phase = null;
        // Start GVN table
        _gvn = new HashMap<>();
        // Allow whole-suite worklist-order sweeps without rewriting the many
        // explicit seeds used by tests and command-line compilation.
        String iterSeed = System.getProperty("simple.iter.seed");
        _iter = new IterPeeps(iterSeed == null ? workListSeed : Long.parseLong(iterSeed));
        // End points of graph
        _stop = new StopNode().init();
        _start = new StartNode(null,_stop,arg).init();
        ZERO  = con(TypeInteger.ZERO).keep();
        XCTRL = new XCtrlNode().peephole().keep();
        P = new Parser(this);
    }


    // Run the requested phases. ELF output contains machine code and data.

    // No code emission, just IR generation
    public CodeGen driver( Phase phase ) { return driver(phase,null,null,false,false,0); }
    // No object file writing, but code generation for a specific cpu/os pair (allows emulation)
    public CodeGen driver( Phase phase, String cpu, String callingConv ) {
        if( phase.ordinal() < Phase.Export.ordinal() )
            // No code outputted
            return driver(phase,cpu,callingConv,false,false,0);
        if( _srcName == null )
            throw new RuntimeException("No source filename provided, so do not know how to name the obj file");
        boolean emitEntrySymbol = !_srcName.contains(".");
        return driver( phase, cpu, callingConv, false, emitEntrySymbol, 0 );
    }
    // Write an object file for a specific cpu/os pair

    public CodeGen driver( String cpu, String callingConv, boolean inMemory, boolean emitEntrySymbol ) { return driver(Phase.Export,cpu,callingConv,inMemory,emitEntrySymbol,0); }

    // The Generic driver
    public CodeGen driver( Phase target, String cpu, String callingConv, boolean inMemory, boolean emitEntrySymbol, int dump ) {
        // Parse the entry source and discover all referenced source files.
        step(target, Phase.Parse,        this::parse,        dump);
        // Run peepholes to a fixed point
        step(target, Phase.Iter,         this::iter,         dump);
        // Run SCCP (and resolve int/flt expressions)
        step(target, Phase.Opto,         this::opto,         dump);
        // Run peepholes to a fixed point again, on the better types.
        step(target, Phase.IterPostOpto, this::iterPostOpto, dump);
        // Field layouts; field symbols become constant offsets
        step(target, Phase.FinalizeIR,   this::finalizeIR,   dump);
        // Final error check - bad programs fail here
        step(target, Phase.TypeCheck,    this::typeCheck,    dump);
        // Replace "fat" nodes with their exploded explicit IR; this can
        // introduce CFG diamonds
        step(target, Phase.Lower,        this::lower,        dump);
        // Compute the loop-tree, used for approximating exec frequencies for
        // scheduling and register allocation
        step(target, Phase.LoopTree,     this::loopTree,     dump);
        // Instruction selection
        step(target, Phase.Select, () -> instSelect(cpu,callingConv), dump);
        // Global Code Motion
        step(target, Phase.Schedule,     this::GCM,          dump);
        // Local (basic block only) scheduling
        step(target, Phase.LocalSched,   this::localSched,   dump);
        // Register allocation
        step(target, Phase.RegAlloc,     this::regAlloc,     dump);
        // Encode machine instructions into bits
        step(target, Phase.Encoding,     this::encode,       dump);
        // Export machine code and data into one ELF file
        step(target, Phase.Export, () -> exportELF(inMemory,emitEntrySymbol), dump);
        return this;
    }

    // The driver names the order; optional phases decide locally whether to run.
    private void step(Phase target, Phase next, Runnable work, int dump) {
        if( next.ordinal() > target.ordinal() || (_phase!=null && _phase.ordinal()>=next.ordinal()) ) return;
        Phase prior = _phase;
        work.run();
        if( _phase!=prior ) dump(dump);
    }

    // All phase entry points share ordering checks, timing, and viewer notification.
    // Record elapsed time even on failure, but report completion only on success.
    CodeGen phase(Phase next, boolean ordered, Runnable work) {
        assert ordered : "Cannot run "+next+" after "+_phase;
        _phase = next;
        long t0 = System.currentTimeMillis();
        work.run();             // Do The Phase
        _times[next.ordinal()] = System.currentTimeMillis() - t0;
        if( _obs!=null ) _obs.phase(next.name()); // Record for viewer
        return this;
    }

    public String entryClinitName() {
        return Parser.addClzPrefix(_srcName==null ? "Test" : _srcName.replace('/','.'))+".<clinit>";
    }

    // Verbose printing during compilation
    private void dump(int dump) {
        int p2 = _phase.ordinal();
        if( (dump & (1<<p2)) == 0 ) return;
        if( (dump & (1<<30)) != 0 )
            System.err.println("After "+_phase+":");
        System.err.println(_phase.ordinal() >= Phase.LocalSched.ordinal()
                           ? asm(new SB()).toString()
                           : IRPrinter.prettyPrint(this));
    }


    // Record times by phase
    public final long[] _times = new long[Phase.LastPhase.ordinal()];

    // ---------------------------
    /**
     *  A counter, for unique node id generation.  Starting with value 1, to
     *  avoid bugs confusing node ID 0 with uninitialized values.
     */
    public int _uid = 1;
    public int UID() { return _uid; }
    public int getUID() {
        return _uid++;
    }


    // Allocate dense alias/function/call IDs within this invocation.
    // Class slots let a forward reference reserve an initializer's identity.
    public final LocalIds _aliases = new LocalIds();
    public int alias(String clz) { return _aliases.next(clz); }

    // C storage has one global alias per symbol, including across object files.
    public int externAlias(String name) { return _aliases.next("C:"+name,LocalIds.RESERVED); }

    // External data addresses supplied by an in-memory linker (image offsets).
    public final HashMap<String,Integer> _externDataAddresses = new HashMap<>();

    // Compute local function index (FIDX) from global function info.  This is
    // called *in order* during parsing, and that order is part of the global
    // unique mapping
    public final LocalIds _fidxs = new LocalIds();
    // Return local index for specific global file & order.  Used to find fidxs for e.g. FREF class <init> fcns
    public int fidx( String clz, int order ) { return _fidxs.next(clz, order); }
    // New fidx in the current file
    public int fidx( String clz ) { return fidx(clz, -1); }

    // Compute local RPC index from global RPC info, one per call
    public final LocalIds _rpcs = new LocalIds();
    public int rpc( String clz ) { return _rpcs.next(clz); }


    // Inlining can insert CFG edges and invalidate cached depth ordering.
    private char _iDepthVersion;
    public char iDepthVersion() { return _iDepthVersion; }
    public void invalidateIDepthCaches() {
        assert _iDepthVersion < Character.MAX_VALUE : "Dominator cache version exceeds 65535";
        ++_iDepthVersion;
    }

    // Popular visit bitset, declared here, so it gets reused all over
    public final BitSet _visit = new BitSet();
    public BitSet visit() { assert _visit.isEmpty(); return _visit; }

    // Start and Stop; end points of the generated IR
    public StartNode _start;
    public StopNode  _stop;

    // Global Value Numbering.  Hash over opcode and inputs; hits in this table
    // are structurally equal.
    public final HashMap<Node,Node> _gvn;

    // Reverse from a constant function pointer to the IR function being called.
    // Error to call with a non-constant TFP
    public FunNode link( TypeFunPtr tfp ) { return link(tfp.fidx());  }
    // Read-only linker lookup, suitable for printers and debugger display.
    // In particular, do not lazily remove a partially dead function.
    public FunNode _link(int fidx) { return _linker.atX(fidx); }
    public FunNode _link(TypeFunPtr tfp) { return _link(tfp.fidx()); }
    // Return the FunNode from a fidx
    public FunNode link( int fidx ) {
        FunNode fun =_linker.atX(fidx);
        if( fun!=null && fun.isDead() ) {
            _linker.setX(fidx,null); fun = null; }
        return fun;
    }
    // Return the <clinit> for a CompUnit
    public FunNode link( CompUnit cu ) {
        for( FunNode fun : _linker )
            if( fun!=null && fun._compunit == cu && fun.isClz() )
                return fun;
        return null;
    }

    // Insert linker mapping from constant function signature to the function
    // being called.
    public void link(FunNode fun) {
        int fidx = fun.sig().fidx();
        _linker.setX(fidx,fun);
    }

    // "Linker" mapping from constant TypeFunPtrs to heads of function.  These
    // TFPs all have exact single fidxs and their return is wiped to BOTTOM (so
    // the return is not part of the match).
    public final Ary<FunNode> _linker = new Ary<>(FunNode.class);
    public final Ary<CallNode> _calls = new Ary<>(CallNode.class);

    // Extern function declarations; input is the FIDX assigned to the name.
    final HashMap<Integer,String> _externFunc = new HashMap<>();
    public void externFunc(int fidx, String ex) {
        assert !_externFunc.containsKey(fidx);
        _externFunc.put(fidx,ex);
    }
    public String externFunc(int fidx) { return _externFunc.get(fidx); }

    public String funcName(int fidx ) {
        FunNode fun = _link(fidx);
        if( fun!=null ) return fun._name;
        return externFunc(fidx);
    }

    public boolean owns(FunNode fun) {
        return fun._compunit != null && fun._compunit._src != null;
    }


    // ---------------------------
    // Parser object
    public final Parser P;

    // Parse ASCII text into Sea-of-Nodes IR
    public CodeGen parse() { return phase(Phase.Parse, _phase==null, () -> ParseAll.parse(this,_src)); }


    // ---------------------------
    // Iterator peepholes.
    public final IterPeeps _iter;
    // Stop tracking deps while assertin
    public boolean _midAssert;
    // Statistics on peepholes
    public int _iter_cnt, _iter_nop_cnt;
    // Stat gathering
    public void iterCnt() { if( !_midAssert ) _iter_cnt++; }
    public void iterNop() { if( !_midAssert ) _iter_nop_cnt++; }

    // Pessimistic peepholes after parsing.  This lifts loaded and parsed
    // types before the optimistic Opto pass resets types and lets them fall.
    public CodeGen iter() { return phase(Phase.Iter, _phase==Phase.Parse, () -> Iter.run(this)); }

    // SCCP and numeric-mode resolution; leave cleanup work queued for Iter.
    public CodeGen opto() { return phase(Phase.Opto, _phase==Phase.Iter, () -> Opto.opto(this)); }

    public CodeGen iterPostOpto() { return phase(Phase.IterPostOpto, _phase==Phase.Opto, () -> _iter.iterate(this)); }

    public CodeGen finalizeIR() { return phase(Phase.FinalizeIR, _phase==Phase.IterPostOpto, () -> FinalizeIR.run(this)); }

    // Root a surviving function without claiming that it has unknown callers.
    void keepInCompUnit(FunNode fun) {
        StopCUNode stop = fun._compunit._stop;
        if( stop._inputs.find(fun.ret()) == -1 )
            add(stop).addDef(fun.ret());
    }
    public <N extends Node> N add( N n ) { return _iter.add(n); }
    public void addAll( Ary<Node> ary ) { _iter.addAll(ary); }
    public void addAll( Node n ) { _iter.add(n); _iter.addAll(n._inputs); }

    // ---------------------------
    // Last check for bad programs
    public CodeGen typeCheck() { return phase(Phase.TypeCheck, _phase==Phase.FinalizeIR, () -> TypeCheck.run(this)); }

    // ---------------------------
    // TypeCheck queued surviving CLInits during its existing error walk.
    public CodeGen lower() {
        return phase(Phase.Lower, _phase==Phase.TypeCheck, () -> _iter.iterate(this));
    }

    // ---------------------------
    // Build the loop tree; break never-exit loops
    public CodeGen loopTree() {
        return phase(Phase.LoopTree, _phase==Phase.Lower, () -> { _start.buildLoopTree(_linker,_stop); FinishCalls.run(this); });
    }

    // ---------------------------


    // ---------------------------
    // Code generation CPU target
    public Machine _mach;
    // Chosen calling convention (usually either Win64 or SystemV)
    public String _callingConv;
    public String _cCallingConv;
    // Callee save registers
    public RegMask _callerSave;
    public RegMask _cCallerSave;

    // All returns have the following inputs:
    // 0 - ctrl
    // 1 - memory
    // 2 - varies with returning GPR,FPR
    // 3 - RPC
    // 4+- Caller save registers
    public RegMask[] _retMasks;
    // Return Program Counter
    public RegMask _rpcMask;

    // Convert to target hardware nodes
    public CodeGen instSelect( String cpu, String callingConv ) { return instSelect(cpu,callingConv,PORTS); }
    public CodeGen instSelect( String cpu, String callingConv, String base ) {
        return InstSelect.select(this,cpu,callingConv,base);
    }

    // ---------------------------


    // ---------------------------
    // Control Flow Graph in Reverse Post Order.
    public Ary<CFGNode> _cfg = new Ary<>(CFGNode.class);

    // Global schedule (code motion) nodes
    public CodeGen GCM() {
        return phase(Phase.Schedule, (_phase==Phase.LoopTree || _phase==Phase.Select), () -> GlobalCodeMotion.buildCFG(this));
    }

    // ---------------------------
    // Local (basic block) scheduler phase, a classic list scheduler
    public CodeGen localSched() { return phase(Phase.LocalSched, _phase==Phase.Schedule, () -> ListScheduler.sched(this)); }


    // ---------------------------
    // Register Allocation
    public RegAlloc _regAlloc;
    public CodeGen regAlloc() {
        return phase(Phase.RegAlloc, _phase==Phase.LocalSched, () -> {
            _regAlloc = new RegAlloc(this);
            _regAlloc.regAlloc();
        });
    }

    // Human-readable register name
    public String reg(Node n) {
        if( _phase.ordinal() >= Phase.RegAlloc.ordinal() ) {
            String s = _regAlloc.reg(n);
            if( s!=null ) return s;
        }
        return "N"+ n._nid;
    }


    // ---------------------------
    // Encoding
    public Encoding _encoding;
    public CodeGen encode() { return phase(Phase.Encoding, _phase==Phase.RegAlloc, () -> _encoding = new Encoding(this).encode()); }

    // ---------------------------
    // Exporting to external formats
    ElfWriter _elf;
    public CodeGen exportELF( boolean inMemory, boolean emitEntrySymbol ) {
        return phase(Phase.Export, _phase==Phase.Encoding, () -> {
            if( inMemory ) new LinkMem(this).link(_encoding);
            else           _elf = new ElfWriter(this).export(emitEntrySymbol);
        });
    }


    // ---------------------------

    public boolean _asmLittle=true;
    public String asm() { return asm(new SB()).toString(); }
    SB asm(SB sb) {
        ASMPrinter.print(sb,this);
        return sb;
    }


    // Testing shortcuts
    public Node ctrl() { return compunit()._stop.ret().ctrl(); }
    public Node expr() { return compunit()._stop.ret().expr(); }
    public String print() { return compunit()._stop.print(); }

    // Debugging helper
    @Override public String toString() {
        if( _stop == null )
            return "No StopNode";
        return IRPrinter.prettyPrint(this);
    }

    // Debugging helper
    public Node f(int idx) {
        for( CompUnit cu : _compunits.values() ) {
            Node n = (cu._stop == null ? _stop : cu._stop).find(idx);
            if( n != null ) return n;
        }
        return null;
    }


    String printCFG() {
        if( _cfg==null ) return "no CFG";
        SB sb = new SB();
        for( CFGNode cfg : _cfg ) {
            sb.fix(8,""+(int)cfg._idepth);
            IRPrinter.printLine( cfg, sb );
        }
        return sb.toString();
    }

    public static void print_as_hex(Encoding enc) {
        for (byte b : enc._bits.toByteArray()) {
            System.out.print(String.format("%02X", b));
        }
        System.out.println();
    }
}
