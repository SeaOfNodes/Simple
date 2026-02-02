package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Opto,                   // Run ideal optimizations
        TypeCheck,              // Last check for bad programs
        LoopTree,               // Build a loop tree; break infinite loops
        Serialize,              // Serialize public IR for future compiles to link
        Select,                 // Convert to target hardware nodes
        Unlink,                 // Unlink call sites; this point keeps moving backwards
        Schedule,               // Global schedule (code motion) nodes
        LocalSched,             // Local schedule
        RegAlloc,               // Register allocation
        Encoding,               // Encoding
        Export,                 // Export
        LastPhase               // After last phase
    }
    public Phase _phase;

    // ---------------------------
    // Module Source Root
    public final String _modDir;
    // Module Build Root
    public final String _buildDir;
    // Search path from CWD for `.o` files containing external symbols and types.
    // This can contain archives, zips and other .o file containers.
    public final Ary<String> _externPaths;

    // Dotted path from module root to file containing the source, sans ".smp".
    // e.g. module root: "sys", nested source name "sys.io".
    public final String _srcName;
    // Compilation source code
    public final String _src;

    // Current Working Directory; default module base
    private final String _cwd;



    // ---------------------------
    // Test setup; no module nor file with specific argument
    public CodeGen( String src ) { this(src, 123L ); }

    // Test setup; no module nor file; can alter seed & argument; can re-run same CodeGen
    public CodeGen( String src, Type arg ) {
        this(null,null,null,"Test", src, 123L, arg);
    }
    // Test setup; no module nor file; can alter seed & argument; can re-run same CodeGen
    public CodeGen( String src, long workListSeed ) {
        this(null,null,null,"Test", src, workListSeed, TypeInteger.BOT);
    }

    // Generic CodeGen, including full module setup
    public CodeGen( String modDir, String buildDir, Ary<String> externPaths,
                    String srcName, String src, long workListSeed, Type arg ) {
        // Public singleton to avoid passing about this state to a huge count
        // of places.  Probably becomes a TLS at some point.
        CODE = this;

        _cwd = System.getProperty("user.dir")+"/";
        _modDir   =   modDir == null ? _cwd :   modDir;
        _buildDir = buildDir == null ? _cwd : buildDir;
        _externPaths = externPaths;
        _srcName = srcName;
        _phase = null;
        _callingConv = null;
        _start = new StartNode(arg);
        _stop = new StopNode(src);
        _src = src;
        _iter = new IterPeeps(workListSeed);
        P = new Parser(this );
        _publishSymbols = new Ary<>(TypeStruct.class);
    }


    // Run requested phases.

    // No code emission, just IR generation
    public CodeGen driver( Phase phase ) { return driver(phase,null,null,null,false,0); }
    // No object file writing, but code generation for a specific cpu/os pair (allows emulation)
    public CodeGen driver( Phase phase, String cpu, String callingConv  ) { return driver(phase,cpu,callingConv,null,false,0); }
    // Write an object file for a specific cpu/os pair
    public CodeGen driver( String cpu, String callingConv, String obj, boolean main ) { return driver(Phase.Export,cpu,callingConv,obj,main,0); }
    // Generic driver
    public CodeGen driver( Phase phase, String cpu, String callingConv, String obj, boolean main, int dump ) {
        int p1 = phase.ordinal(), p2 = _phase==null ? -1 : _phase.ordinal();
        if( p2 < p1 && p2 <  Phase.Parse     .ordinal() ) { parse();     p2 = dump(dump); }
        if( p2 < p1 && p2 <  Phase.Opto      .ordinal() ) { opto();      p2 = dump(dump); }
        if( p2 < p1 && p2 <  Phase.TypeCheck .ordinal() ) { typeCheck(); p2 = dump(dump); }
        if( p2 < p1 && p2 <  Phase.LoopTree  .ordinal() ) { loopTree();  p2 = dump(dump); }
        if( p2 < p1 && p1 >= Phase.Export    .ordinal() ) { serialize(); p2 = dump(dump); } // Include ideal graph in object file
        if( p2 < p1 && p2 <  Phase.Select    .ordinal() && cpu != null ) { instSelect(cpu,callingConv); p2 = dump(dump); }
        if( p2 < p1 && p2 <  Phase.Unlink    .ordinal() ) { unlink();    p2 = dump(dump); }
        if( p2 < p1 && p2 <  Phase.Schedule  .ordinal() ) { GCM();       p2 = dump(dump); }
        if( p2 < p1 && p2 <  Phase.LocalSched.ordinal() ) { localSched();p2 = dump(dump); }
        if( p2 < p1 && p2 <  Phase.RegAlloc  .ordinal() ) { regAlloc();  p2 = dump(dump); }
        if( p2 < p1 && p2 <  Phase.Encoding  .ordinal() ) { encode();    p2 = dump(dump); }
        if( p2 < p1 && p2 <  Phase.Export    .ordinal() ) { exportELF(obj,main); p2 = dump(dump); }
        return this;
    }

    // Verbose printing during compilation
    private int dump(int dump) {
        int p2 = _phase.ordinal();
        if( (dump & (1<<p2)) == 0 ) return p2;
        if( (dump & (1<<29)) != 0 ) {
            String fn = ""+p2+"-"+_phase+".dot";
            try {
                Files.writeString(Path.of(fn),
                                  new GraphVisualizer().generateDotOutput(_stop, null, null));
            } catch(IOException e) { throw Utils.TODO("Cannot write DOT file"); }
            return p2;
        }

        if( (dump & (1<<30)) != 0 )
            System.err.println("After "+_phase+":");
        System.err.println(IRPrinter.prettyPrint(_stop, 9999));
        return p2;
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

    // Next available memory alias number
    int _alias = 2; // 0 is for control, 1 for memory
    public int nextALIAS() { return _alias++; }


    // idepths are cached and valid until *inserting* CFG edges (deleting is
    // OK).  This happens with inlining, which bumps the version to bulk
    // invalidate the idepth caches.
    private int _iDepthVersion = 0;
    public void invalidateIDepthCaches() { _iDepthVersion++; }
    public boolean validIDepth(int idepth) {
        if( idepth==0 ) return false;
        if( _iDepthVersion==0 ) return true;
        return (idepth%100)==_iDepthVersion;
    }
    public int iDepthAt(int idepth) {
        return 100*idepth+_iDepthVersion;
    }
    public int iDepthFrom(int idepth) {
        assert idepth==0 || validIDepth(idepth);
        return idepth+100;
    }

    // Popular visit bitset, declared here, so it gets reused all over
    public final BitSet _visit = new BitSet();
    public BitSet visit() { assert _visit.isEmpty(); return _visit; }

    // Start and stop; end points of the generated IR
    public StartNode _start;
    public StopNode  _stop;

    // Global Value Numbering.  Hash over opcode and inputs; hits in this table
    // are structurally equal.
    public final HashMap<Node,Node> _gvn = new HashMap<>();

    // Source of unique function indices
    private int _fidx =0;
    public int nextFIDX() { return _fidx++; }
    public TypeFunPtr makeFun( TypeFunPtr fun ) {
        assert fun.nfcns() == Integer.MAX_VALUE; // Not assigned yet
        return fun.makeFrom(nextFIDX());
    }


    // Reverse from a constant function pointer to the IR function being called.
    // Error to call with a non-constant TFP
    public FunNode link( TypeFunPtr tfp ) { return link(tfp.fidx());  }
    // Return the FunNode from a fidx
    public FunNode link( int fidx ) {
        FunNode fun =_linker.atX(fidx);
        if( fun!=null && fun.isDead() ) {
            _linker.setX(fidx,null); fun = null; }
        return fun;
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
    final Ary<FunNode> _linker = new Ary<>(FunNode.class);

    // Extern function declarations; input is the FIDX assigned to the name.
    final HashMap<Integer,String> _externFunc = new HashMap<>();
    public void externFunc(int fidx, String ex) {
        assert !_externFunc.containsKey(fidx);
        _externFunc.put(fidx,ex);
    }
    public String externFunc(int fidx) { return _externFunc.get(fidx); }

    public String funcName(int fidx ) {
        FunNode fun = link(fidx);
        if( fun!=null ) return fun._name;
        return externFunc(fidx);
    }


    // ---------------------------
    // Parser object
    public final Parser P;
    // Public exposed names; TODO: only ever publish the file-as-struct symbol
    public final Ary<TypeStruct> _publishSymbols;

    // Parse ASCII text into Sea-of-Nodes IR
    public CodeGen parse() {
        assert _phase == null;
        _phase = Phase.Parse;
        long t0 = System.currentTimeMillis();

        P.parse();

        _times[Phase.Parse.ordinal()] = System.currentTimeMillis() - t0;
        JSViewer.show();
        return this;
    }


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

    // Run ideal optimizations
    public CodeGen opto() {
        assert _phase == Phase.Parse;
        _phase = Phase.Opto;
        long t0 = System.currentTimeMillis();

        Opto.opto(this);

        _times[Phase.Opto.ordinal()] = System.currentTimeMillis() - t0;
        return this;
    }
    public <N extends Node> N add( N n ) { return _iter.add(n); }
    public void addAll( Ary<Node> ary ) { _iter.addAll(ary); }
    public void addAll( Node n ) { _iter.add(n); _iter.addAll(n._inputs); }

    // ---------------------------
    // Last check for bad programs
    public CodeGen typeCheck() {
        // Demand phase Opto for cleaning up dead control flow at least,
        // required for the following GCM.
        assert _phase.ordinal() <= Phase.Opto.ordinal();
        _phase = Phase.TypeCheck;
        long t0 = System.currentTimeMillis();

        Parser.ParseException err = _stop.walk( Node::err );
        _times[Phase.TypeCheck.ordinal()] = System.currentTimeMillis() - t0;
        if( err != null )
            throw err;
        return this;
    }

    // ---------------------------
    // Build the loop tree; break never-exit loops
    public CodeGen loopTree() {
        assert _phase.ordinal() <= Phase.TypeCheck.ordinal();
        _phase = Phase.LoopTree;
        long t0 = System.currentTimeMillis();
        // Build the loop tree, fix never-exit loops
        _start.buildLoopTree(_start,_stop);
        _times[Phase.LoopTree.ordinal()] = System.currentTimeMillis() - t0;
        return this;
    }

    // ---------------------------
    BAOS _serial;
    public void serialize() {
        assert _phase.ordinal() <= Phase.LoopTree.ordinal();
        _phase = Phase.Serialize;
        long t0 = System.currentTimeMillis();
        // Does not change compiler phase; just records IR
        _serial = Serialize.serialize(this);
        _times[Phase.Serialize.ordinal()] = System.currentTimeMillis() - t0;
    }

    // ---------------------------
    public void unlink() {
        assert _phase.ordinal() <= Phase.Select.ordinal();
        _phase = Phase.Unlink;
        long t0 = System.currentTimeMillis();

    	// The remaining passes assume all calls are unlinked; i.e. we are throwing
    	// away the Call Graph here.  Functions only reachable from internal calls
    	// need to be re-hooked to stop/start less they go dead.
        _start.walk( x -> {
                // Unlink all existing (conservative) linkages.
                if( x instanceof CallNode call ) {
                    for( Node y : call._outputs ) {
                        // Keep function alive and hooked to Start so it gets
                        // codegen'd.  Function is callable by this call via
                        // TFP, although perhaps not by any other means.
                        if( y instanceof FunNode fun && !(fun.in(1) instanceof StartNode) ) {
                            if( fun.rpc()==null ) // Ensure valid RPC
                                makeRPC(fun);
                            // Insert a hook to Start and Stop
                            fun.insertDef(1,_start);
                            for( Node use : fun._outputs )
                                if( use instanceof ParmNode parm )
                                    parm.insertDef(1,ConstantNode.make(parm._type).peephole());
                            _stop.addDef(fun.ret());
    	                    }
                    }
                    call.unlink_all();
                }
                return null;
            } );

        _times[Phase.Unlink.ordinal()] = System.currentTimeMillis() - t0;
    }
    // Make a valid Parm RPC.
    // First make sure a valid RPC; single-call into multi-fun will have each
    // function having a single call site, so the RPC becomes a constant and
    // folds - but since multiple targets, the Call never inlines.  Recreate a
    // valid RPC so codegen (and Eval2) understands the calling convention.
    private static void makeRPC( FunNode fun ) {
        ParmNode rpc = new ParmNode("$rpc",0,fun.ret().rpc()._type,fun);
        for( int i=1; i<fun.nIns(); i++ ) {
            CallEndNode cend = ((CallNode)fun.in(i)).cend();
            rpc.addDef(ConstantNode.make(cend._rpc).peephole());
        }
        fun.ret().setDef(3,rpc.init());
    }


    // ---------------------------
    // Code generation CPU target
    public Machine _mach;
    // Chosen calling convention (usually either Win64 or SystemV)
    public String _callingConv;
    // Callee save registers
    public RegMask _callerSave;

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
        assert _phase.ordinal() <= Phase.Unlink.ordinal();
        _phase = Phase.Select;

        _callingConv = callingConv;

        // Not timing the class load...
        // Look for CPU in fixed named place:
        //   com.seaofnodes.simple.node.cpus."cpu"."cpu.class"
        String clzFile = base+"."+cpu+"."+cpu;
        try { _mach = ((Class<Machine>) Class.forName( clzFile )).getDeclaredConstructor(new Class[]{CodeGen.class}).newInstance(this); }
        catch( Exception e ) { throw new RuntimeException(e); }

        // Build global copies of common register masks.
        long callerSave = _mach.callerSave();
        long  neverSave = _mach. neverSave();
        int maxReg = Math.min(64,_mach.regs().length);
        assert maxReg>=64 || (-1L << maxReg & callerSave)==0; // No stack slots in callerSave
        _callerSave = new RegMask(callerSave);

        // Build a Return RegMask array.  All returns have the following inputs:
        // 0 - ctrl
        // 1 - memory
        // 2 - varies with returning GPR,FPR
        // 3 - RPC
        // 4+- Caller save registers
        _retMasks = new RegMask[(maxReg-_callerSave.size()-Long.bitCount( neverSave ))+4];
        for( int reg=0, i=4; reg<maxReg; reg++ )
            if( !_callerSave.test(reg) && ((1L<<reg)&neverSave)==0 )
                _retMasks[i++] = new RegMask(reg);
        _rpcMask = new RegMask(_mach.rpc());
        _retMasks[3] = _rpcMask;

        // Convert to machine ops
        long t0 = System.currentTimeMillis();
        _uid = 1;               // All new machine nodes reset numbering
        var map = new IdentityHashMap<Node,Node>();
        _instSelect( _stop, map );
        _stop  = ( StopNode)map.get(_stop );
        StartNode start = (StartNode)map.get(_start);
        _start = start==null ? new StartNode(_start) : start;
        _instOuts(_stop,visit());
        _visit.clear();

        _times[Phase.Select.ordinal()] = System.currentTimeMillis() - t0;
        return this;
    }

    // Walk all ideal nodes, recursively mapping ideal to machine nodes, then
    // make a machine node for "this".
    private Node _instSelect( Node n, IdentityHashMap<Node,Node> map ) {
        if( n==null ) return null;
        Node x = map.get(n);
        if( x !=null ) return x; // Been there, done that

        // If n is a MachNode already, then its part of a multi-node expansion.
        // It does not need instruction selection (already selected!)
        // but it does need its inputs walked.
        if( n instanceof MachNode ) {
            for( int i=0; i < n.nIns(); i++ )
                n._inputs.set(i, _instSelect(n.in(i),map) );
            return n;
        }

        // Produce a machine node from n; map it to flag as done so stops cycles.
        map.put(n, x=_mach.instSelect(n) );
        // Walk machine op and replace inputs with mapped inputs
        for( int i=0; i < x.nIns(); i++ )
            x._inputs.set(i, _instSelect(x.in(i),map) );
        // Post selection action
        if( x instanceof MachNode mach ) {
            if( n instanceof ReturnNode ret )
                ((ReturnNode)mach)._fun = (FunNode)map.get(ret._fun);
            mach.postSelect(this);  // Post selection action
        }

        return x;
    }

    // Walk all machine Nodes, and set their output edges
    private void _instOuts( Node n, BitSet visit ) {
        if( visit.get(n._nid) ) return;
        visit.set(n._nid);
        for( Node in : n._inputs )
            if( in!=null ) {
                in._outputs.push(n);
                // CallNode special: outputs are partially ordered; CallEnd in slot 0
                if( in instanceof CallNode call && n instanceof CallEndNode cend && call.out(0) != cend )
                    call._outputs.swap(0,call.nOuts()-1);
                _instOuts(in,visit);
            }
    }


    // ---------------------------
    // Control Flow Graph in Reverse Post Order.
    public Ary<CFGNode> _cfg = new Ary<>(CFGNode.class);

    // Global schedule (code motion) nodes
    public CodeGen GCM() { return GCM(false); }
    public CodeGen GCM( boolean show) {
        assert _phase.ordinal() <= Phase.Unlink.ordinal();
        _phase = Phase.Schedule;
        long t0 = System.currentTimeMillis();

        GlobalCodeMotion.buildCFG(this);
        _times[Phase.Schedule.ordinal()] = System.currentTimeMillis() - t0;
        if( show )
            System.out.println(new GraphVisualizer().generateDotOutput(_stop,null,null));
        return this;
    }

    // ---------------------------
    // Local (basic block) scheduler phase, a classic list scheduler
    public CodeGen localSched() {
        assert _phase == Phase.Schedule;
        _phase = Phase.LocalSched;
        long t0 = System.currentTimeMillis();
        ListScheduler.sched(this);
        _times[Phase.LocalSched.ordinal()] = System.currentTimeMillis() - t0;
        return this;
     }


    // ---------------------------
    // Register Allocation
    public RegAlloc _regAlloc;
    public CodeGen regAlloc() {
        assert _phase == Phase.LocalSched;
        _phase = Phase.RegAlloc;
        long t0 = System.currentTimeMillis();
        _regAlloc = new RegAlloc(this);
        _regAlloc.regAlloc();
        _times[Phase.RegAlloc.ordinal()] = System.currentTimeMillis() - t0;
        return this;
    }

    // Human readable register name
    public String reg(Node n) { return reg(n,null); }
    public String reg(Node n, FunNode fun) {
        if( _phase.ordinal() >= Phase.RegAlloc.ordinal() ) {
            String s = _regAlloc.reg(n,fun);
            if( s!=null ) return s;
        }
        return "N"+ n._nid;
    }


    // ---------------------------
    // Encoding
    public Encoding _encoding;   // Encoding object
    public void preEncode() {  } // overridden by alternative ports
    public CodeGen encode() {
        assert _phase == Phase.RegAlloc;
        _phase = Phase.Encoding;
        long t0 = System.currentTimeMillis();
        _encoding = new Encoding(this);
        preEncode();
        _encoding.encode();
        _times[Phase.Encoding.ordinal()] = System.currentTimeMillis() - t0;
        return this;
    }

    // ---------------------------
    // Exporting to external formats
    public CodeGen exportELF(String fname, boolean main) {
        assert _phase == Phase.Encoding;
        _phase = Phase.Export;
        long t0 = System.currentTimeMillis();
        if( fname == null ) new LinkMem(this).link(); // In memory patching
        else new ElfWriter(this).export(fname,main); // External ELF file
        _times[Phase.Export.ordinal()] = System.currentTimeMillis() - t0;
        return this;
    }

    // ---------------------------

    // Search a external path list, each path is recursively searched for .o
    // files, which are partially loaded and searched for public symbols.

    // This is a state machine which lazily searches down the set of search
    // paths until the requested module is class is found.

    // Map from external Strings to either partially read ElfFile or ExternNode
    public final HashMap<String,Object> _externSymbols = new HashMap<>();
    private int _extPathIdx;    // Search index into the extern path list
    private final Ary<File> _files = new Ary<>(File.class);   // Files in the current extern path being searched
    private int _extFileIdx;    // Index into _files
    public ExternNode findExternal( String name ) {

        while( true ) {
            // Check for an immediate hit
            switch( _externSymbols.get(name) ) {
            case ExternNode extern:
                return extern;
            case ElfReader elf:
                // Convert ElfReader
                Ary<TypeStruct> published = elf.loadSimple();
                for( TypeStruct ts : published )
                    _externSymbols.put(ts._name, new ExternNode(ts,ts._name));
                break;

            case null:
                // If a miss, attempt to load and search the next ELF file.
                if( _extFileIdx >= _files._len ) {
                    if( _externPaths==null || _extPathIdx >= _externPaths._len )
                        return null; // A true miss in the whole search path
                    loadExtPath();   // Load next batch file of files
                    break;
                }
                // Load and parse an ELF header
                ElfReader elf = ElfReader.load(_files.at(_extFileIdx++));
                if( elf == null ) break; // Not an ELF
                // Load public symbols from the ELF
                elf.loadPublicSymbols();
                if( elf._strs==null ) break; // No .simple section with strings
                // Put public symbols in global table; first names shadow all others
                for( String s : elf._strs )
                    if( !s.isEmpty() )
                        // Map from symbol string to ELFReader; most lookups
                        // will miss and no need to unpack ElfReader types
                        _externSymbols.putIfAbsent(s,elf);
                break;

            default: throw Utils.TODO("should not reach here");
            }
        }
    }

    // Load all the .o files in the next external path.
    private void loadExtPath() {
        _files.clear();
        _extFileIdx = 0;
        String extpath = _externPaths.at(_extPathIdx++);
        File f = new File(_cwd+extpath);
        fillExtFiles(f);
    }

    // Recursive search (TODO: gzip, archives) and gather all .o files.
    private void fillExtFiles(File dir) {
        if( dir.isDirectory() )
            for( File f : dir.listFiles() )
                fillExtFiles(f);
        else if( dir.getName().endsWith(".o") )
            _files.add(dir);
    }


    // ---------------------------
    public boolean _asmLittle=true;
    SB asm(SB sb) { return ASMPrinter.print(sb,this); }
    public String asm() { return asm(new SB()).toString(); }


    // Testing shortcuts
    public Node ctrl() { return _stop.ret(this).ctrl(); }
    public Node expr() { return _stop.ret(this).expr(); }
    public String print() { return _stop.print(); }

    // Debugging helper
    @Override public String toString() {
        return _phase.ordinal() > Phase.Schedule.ordinal()
            ? IRPrinter.prettyPrint( this )
            : _stop.p(4999);
    }

    // Debugging helper
    public Node f(int idx) { return _stop.find(idx); }


    String printCFG() {
        if( _cfg==null ) return "no CFG";
        SB sb = new SB();
        for( CFGNode cfg : _cfg ) {
            sb.fix(8,""+cfg._idepth);
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

    public void print_as_hex() {
        for (byte b : _encoding._bits.toByteArray()) {
            System.out.print(String.format("%02X", b));
        }
        System.out.println();
    }
}
