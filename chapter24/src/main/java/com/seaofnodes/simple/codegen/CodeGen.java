package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Var;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("unchecked")
public class CodeGen {
    // Last created CodeGen as a global; used all over to avoid passing about a
    // "context".
    public static CodeGen CODE;

    // CPU port directory; at least x86_64_v2, risc5, arm
    public static final String PORTS = "com.seaofnodes.simple.node.cpus";

    public static final String OS  = System.getProperty("os.name");
    public static final String CPU = System.getProperty("os.arch");

    public static final String CALL_CONVENTION = OS.startsWith("Windows") ? "Win64" : "SystemV";
    public static final String CPU_PORT = switch( CPU ) {
        case "amd64" -> "x86_64_v2";
        default -> throw Utils.TODO("Map Yer CPU Port Here");
    };

    public enum Phase {
        Parse,                  // Parse ASCII text into Sea-of-Nodes IR
        Opto,                   // Run ideal optimizations
        TypeCheck,              // Last check for bad programs
        LoopTree,               // Build a loop tree; break infinite loops
        Select,                 // Convert to target hardware nodes
        Schedule,               // Global schedule (code motion) nodes
        LocalSched,             // Local schedule
        RegAlloc,               // Register allocation
        Encoding,               // Encoding
        Export,                 // Export
    }
    public Phase _phase;

    // ---------------------------
    // Compilation source code
    public final String _root;  // Path to project root    relative to process
    public final String _objs;  // Path to project objects relative to process
    public final Ary<String> _src_paths; // File paths to source, relative to root
    public final Ary<String> _srcs;      // String source

    // ---------------------------

    /***
        Most general case:
        - Needs a $ROOT file path
        - Needs a $OBJS file path
        - Needs a collection of:
        - - [ {src_path,src}, .... ]
        - - - src_path - used to define visibility
        - - - src (not in a file) for direct compilation
        - If parse reveals FRef, lookup IR in $OBJ; lookup SRC in $ROOT
        - - can add a new [src_path,src] pair to compile if out-of-date

        Minimum case
        - ROOT is null/CWD, no public file lookups (pack/private ok)
        - OBJS is null/CWD
        - Collection of 1
        - - src_path is null/CWD, only lookup pack (sideways)
        - - src must exist for parser


     */

    // ---------------------------
    public CodeGen( String src ) { this(src, 123L ); }
    public CodeGen( String src, long workListSeed ) {
        this( null,null,new Ary<>(new String[1]),new Ary<>(new String[]{src}), workListSeed, true );
    }

    public CodeGen( String ROOT, String OBJS, String src_path, String src ) {
        this(ROOT, OBJS, new Ary<>(String.class){{add(src_path);}}, new Ary<>(String.class){{add(src);}}, 123L, true);
    }

    public CodeGen( String ROOT, String OBJS, Ary<String> src_paths, Ary<String> srcs, long workListSeed, boolean reset ) {
        if( srcs._len != 1 ) throw Utils.TODO();
        CODE = this;
        if( reset ) Type.reset();
        _main = makeFun(TypeFunPtr.MAIN);
        _phase = null;
        _callingConv = null;
        _root = ROOT;
        _objs = OBJS;
        _src_paths = src_paths;
        _srcs = srcs;
        _iter = new IterPeeps(workListSeed);
        P = new Parser(this);
    }


    // All passes up to Phase, except ELF
    public CodeGen driver( Phase phase ) { return driver(phase,null,null); }
    public CodeGen driver( Phase phase, String cpu, String callingConv ) {
        if( _phase==null ) parse();
        int p1 = phase.ordinal(), p2;
        p2 = _phase.ordinal(); if( p2 < p1 && p2 <  Phase.Opto      .ordinal() ) opto();
        p2 = _phase.ordinal(); if( p2 < p1 && p2 <  Phase.TypeCheck .ordinal() ) typeCheck();
        p2 = _phase.ordinal(); if( p2 < p1 && p2 <  Phase.LoopTree  .ordinal() ) loopTree();
        p2 = _phase.ordinal(); if( p2 < p1 && p1 >= Phase.Export    .ordinal() ) serialize(); // Include ideal graph in object file
        p2 = _phase.ordinal(); if( p2 < p1 && p2 <  Phase.Select    .ordinal() && cpu != null ) instSelect(cpu,callingConv);
        p2 = _phase.ordinal(); if( p2 < p1 && p2 <  Phase.Schedule  .ordinal() ) GCM();
        p2 = _phase.ordinal(); if( p2 < p1 && p2 <  Phase.LocalSched.ordinal() ) localSched();
        p2 = _phase.ordinal(); if( p2 < p1 && p2 <  Phase.RegAlloc  .ordinal() ) regAlloc();
        p2 = _phase.ordinal(); if( p2 < p1 && p2 <  Phase.Encoding  .ordinal() ) encode();
        return this;
    }

    // Run all the phases through final ELF emission
    public CodeGen driver( String cpu, String callingConv ) throws IOException {
        return driver(Phase.Export,cpu,callingConv).exportELF();
    }


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
    private int _alias = 2; // 0 is for control, 1 for memory
    public  int getALIAS() { return _alias++; }

    // Next available function index
    private int _fidx = 0;
    public int nfidxs() { return _fidx; } // Current max fidx
    // "Linker" mapping from fidx to heads of function.  Functions can die and
    // are lazily nulled out
    private final Ary<FunNode> _linker = new Ary<>(FunNode.class);
    // Get a new fidx/TFP
    public TypeFunPtr makeFun( TypeFunPtr fun ) { return fun.makeFrom(getFIDX()); }
    public int getFIDX() {
        if( _iter!=null ) add(_start);            // Lower set of free escaped fidxs
        assert _fidx<64;        // TODO: need a larger FIDX space
        return _fidx++;
    }
    // A conservative approximation of all exported functions
    public TypeFunPtr allExports() {
        if( _fidx <= 1 )
            return TypeFunPtr.BOT;
        throw Utils.TODO();
    }

    // Signature for MAIN
    public final TypeFunPtr _main;

    // Reverse from a fidx to the IR function being called
    public FunNode link( int fidx ) {
        FunNode fun = _linker.atX(fidx);
        // FunNodes die, lazily remove mappings to dead functions
        if( fun!=null && fun.isDead() ) _linker.setX(fidx,fun=null);
        return fun;
    }

    // Insert linker mapping from constant function signature to the function
    // being called.
    public void link(FunNode fun) {
        int fidx = fun.sig().fidx();
        assert _linker.atX(fidx)==null || _linker.atX(fidx).sig()==fun.sig();
        _linker.setX(fidx,fun);
    }

    public boolean hasMain() {
        return _linker.atX(_main.fidx())!=null;
    }

    // Next available RPC - Return Program Counter
    private int _rpc = 1;
    public int getRPC() { return _rpc++; }

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


    // ---------------------------
    // Parser object
    public final Parser P;

    // Parse ASCII text into Sea-of-Nodes IR
    public int _tParse;
    public CodeGen parse() {
        assert _phase == null;
        _phase = Phase.Parse;

        // Parse all sources, as needed
        for( int i=0; i<_srcs._len; i++ ) {
            long t0 = System.currentTimeMillis();
            P.parse(_srcs.at(i));
            _tParse += (int)(System.currentTimeMillis() - t0);
            linkOrParseV(P._fref_vars );
            linkOrParseT(P._fref_types);
        }


        JSViewer.show();
        return this;
    }

    void linkOrParseV(Ary<Var> frefs) {
        for( Var v : frefs )
            throw Parser.error("Undefined name '" + v._name + "'",v._loc);
    }
    void linkOrParseT(Ary<TypeStruct> frefs) {
        for( TypeStruct t : frefs )
            throw Parser.error("Unknown struct '" + t._name + "'",null);
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
    public int _tOpto;
    public CodeGen opto() {
        assert _phase == Phase.Parse;
        _phase = Phase.Opto;
        long t0 = System.currentTimeMillis();

        // Pessimistic peephole optimization on a worklist
        _iter.iterate(this);

        // OPTIMISTIC PASS GOES HERE

        // Track all functions, including ones alive but not exported.  They
        // still get special treatment, by the pretty-printer at least which
        // wants to group nodes by function.
        TypeFunPtr allEscapes = _start.allEscapes();
        for( int fidx=0; fidx<_linker._len; fidx++ ) {
            if( allEscapes.hasFIDX(fidx) ) continue;
            FunNode fun = link(fidx);
            if( fun!=null && fun.unknownCallers() ) {
                assert hasMain() || !fun.isExported();
                add(fun).setDef(1,Parser.XCTRL); // Kill start input
                addAll(fun._outputs);
                _stop.delDef(_stop._inputs.find(fun.ret()));
                _iter.iterate(this);
            }
        }

        // Freeze field sizes; do struct layouts; convert field offsets into
        // constants.
        for( int i=0; i<_start.nOuts(); i++ ) {
            Node use = _start.out(i);
            if( use instanceof ConFldOffNode off ) {
                TypeMemPtr tmp = (TypeMemPtr) Parser.TYPES.get(off._name);
                off.subsume( off.asOffset(tmp._obj).peephole() );
                i--;
            }
        }
        _iter.iterate(this);

        // To help with testing, sort StopNode returns by NID
        Arrays.sort(_stop._inputs._es,0,_stop.nIns(),(x,y) -> x._nid - y._nid );

        _tOpto = (int)(System.currentTimeMillis() - t0);

        // TODO:
        // Optimistic
        // TODO:
        // loop unroll, peel, RCE, etc
        return this;
    }
    public <N extends Node> N add( N n ) { return _iter.add(n); }
    public void addAll( Ary<Node> ary ) { _iter.addAll(ary); }


    // ---------------------------
    // Last check for bad programs
    public int _tTypeCheck;
    public CodeGen typeCheck() {
        // Demand phase Opto for cleaning up dead control flow at least,
        // required for the following GCM.
        assert _phase.ordinal() <= Phase.Opto.ordinal();
        _phase = Phase.TypeCheck;
        long t0 = System.currentTimeMillis();

        Parser.ParseException err = _stop.walk( Node::err );
        _tTypeCheck = (int)(System.currentTimeMillis() - t0);
        if( err != null )
            throw err;
        return this;
    }

    // ---------------------------
    // Build the loop tree; break never-exit loops
    public int _tLoopTree;
    public CodeGen loopTree() {
        assert _phase.ordinal() <= Phase.TypeCheck.ordinal();
        _phase = Phase.LoopTree;
        long t0 = System.currentTimeMillis();
        // Build the loop tree, fix never-exit loops
        _start.buildLoopTree(_stop);
        _tLoopTree = (int)(System.currentTimeMillis() - t0);
        return this;
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
    public int _tInsSel;
    public CodeGen instSelect( String cpu, String callingConv ) { return instSelect(cpu,callingConv,PORTS); }
    public CodeGen instSelect( String cpu, String callingConv, String base ) {
        assert _phase.ordinal() == Phase.LoopTree.ordinal();
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

        // Remap local references to the machine ops
        _stop  = ( StopNode)map.get(_stop );
        StartNode start = (StartNode)map.get(_start);
        _start = start==null ? new StartNode(_start) : start;
        // Insert output edges
        _instOuts(_stop,visit());
        _visit.clear();
        _tInsSel = (int)(System.currentTimeMillis() - t0);
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
        if( x instanceof MachNode mach )
            mach.postSelect(this);  // Post selection action

        return x;
    }

    // Walk all machine Nodes, and set their output edges
    private void _instOuts( Node n, BitSet visit ) {
        if( visit.get(n._nid) ) return;
        visit.set(n._nid);
        for( Node in : n._inputs )
            if( in!=null ) {
                in._outputs.push(n);
                // Keep invariant the CallEnd is slot 0 output of Call
                if( in instanceof CallNode && n instanceof CallEndNode && in._outputs._len > 1 )
                    in._outputs.swap(0,in._outputs._len-1);
                _instOuts(in,visit);
            }
    }


    // ---------------------------
    // Control Flow Graph in Reverse Post Order.
    public int _tGCM;
    public Ary<CFGNode> _cfg = new Ary<>(CFGNode.class);

    // Global schedule (code motion) nodes
    public CodeGen GCM() { return GCM(false); }
    public CodeGen GCM( boolean show) {
        assert _phase.ordinal() <= Phase.Select.ordinal();
        _phase = Phase.Schedule;
        long t0 = System.currentTimeMillis();


        // Hopefully done with the CG now.  Unlink all linked calls.
        for( int fidx = 0; fidx<_linker._len; fidx++ ) {
            FunNode fun = link(fidx);
            if( fun!=null )
                for( Node c : fun._inputs )
                    if( c instanceof CallNode call )
                        call.unlink_all();
        }


        GlobalCodeMotion.buildCFG(this);
        _tGCM = (int)(System.currentTimeMillis() - t0);
        if( show )
            System.out.println(new GraphVisualizer().generateDotOutput(_stop,null,null,_srcs.at(0)));
        return this;
    }

    // ---------------------------
    // Local (basic block) scheduler phase, a classic list scheduler
    public int _tLocal;
    public CodeGen localSched() {
        assert _phase == Phase.Schedule;
        _phase = Phase.LocalSched;
        long t0 = System.currentTimeMillis();
        ListScheduler.sched(this);
        _tLocal = (int)(System.currentTimeMillis() - t0);
        return this;
     }


    // ---------------------------
    // Register Allocation
    public int _tRegAlloc;
    public RegAlloc _regAlloc;
    public CodeGen regAlloc() {
        assert _phase == Phase.LocalSched;
        _phase = Phase.RegAlloc;
        long t0 = System.currentTimeMillis();
        _regAlloc = new RegAlloc(this);
        _regAlloc.regAlloc();
        _tRegAlloc = (int)(System.currentTimeMillis() - t0);
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
    public int _tEncode;
    public Encoding _encoding;   // Encoding object
    public void preEncode() {  } // overridden by alternative ports
    public CodeGen encode() {
        assert _phase == Phase.RegAlloc;
        _phase = Phase.Encoding;
        long t0 = System.currentTimeMillis();
        _encoding = new Encoding(this);
        preEncode();
        _encoding.encode();
        _tEncode = (int)(System.currentTimeMillis() - t0);
        return this;
    }

    // ---------------------------
    BAOS _serial;
    void serialize() {
        assert _phase.ordinal() == Phase.LoopTree.ordinal();
        _serial = Serialize.serialize(this);
        // Does not change compiler phase; just records IR
    }

    // ---------------------------
    // Exporting to external formats
    public CodeGen exportELF() throws IOException {
        assert _phase == Phase.Encoding;
        _phase = Phase.Export;
        if( _objs == null ) new LinkMem(this).link(); // In memory patching
        else new ElfFile(this).export(); // External ELF file
        return this;
    }


    // ---------------------------
    public boolean _asmLittle=true;
    SB asm(SB sb) { return ASMPrinter.print(sb,this); }
    public String asm() { return asm(new SB()).toString(); }


    // Testing shortcuts
    public Node ctrl() { return _stop.ret().ctrl(); }
    public Node expr() { return _stop.ret().expr(); }
    public String print() { return _stop.print(); }

    // Debugging helper
    @Override public String toString() {
        return IRPrinter.prettyPrint( this );
    }

    // Debugging helper
    public Node f(int idx) { return _stop.find(idx); }


    public String printCFG() {
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

    //// Debug purposes for now
//    public CodeGen printENCODING() {
//        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//
//        for(Node bb : CodeGen.CODE._cfg) {
//            for(Node n: bb.outs()) {
//                if(n instanceof MachNode) {
//                    ((MachNode) n).encoding(E);
//                }
//            }
//        }
//
//        print_as_hex(outputStream);
//        // Get the raw bytes from the output stream
//        return this;
//    }
}
