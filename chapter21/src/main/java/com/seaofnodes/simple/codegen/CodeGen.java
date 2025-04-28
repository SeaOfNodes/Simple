package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.*;
import com.seaofnodes.simple.type.*;
import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;

@SuppressWarnings("unchecked")
public class CodeGen {
    public static final String PORTS = "com.seaofnodes.simple.node.cpus";
    // Last created CodeGen as a global; used all over to avoid passing about a
    // "context".
    public static CodeGen CODE;

    public enum Phase {
        Parse,                  // Parse ASCII text into Sea-of-Nodes IR
        Opto,                   // Run ideal optimizations
        TypeCheck,              // Last check for bad programs
        LoopTree,               // Build a loop tree; break infinite loops
        InstSelect,             // Convert to target hardware nodes
        Schedule,               // Global schedule (code motion) nodes
        LocalSched,             // Local schedule
        RegAlloc,               // Register allocation
        Encoding,               // Encoding
        Export,                 // Export
    }
    public Phase _phase;

    // ---------------------------
    // Compilation source code
    public final String _src;
    // Compile-time known initial argument type
    public final TypeInteger _arg;

    // ---------------------------
    public CodeGen( String src ) { this(src, TypeInteger.BOT, 123L ); }
    public CodeGen( String src, TypeInteger arg, long workListSeed ) {
        CODE = this;
        _phase = null;
        _callingConv = null;
        _start = new StartNode(arg);
        _stop = new StopNode(src);
        _src = src;
        _arg = arg;
        _iter = new IterPeeps(workListSeed);
        P = new Parser(this,arg);
    }


    // All passes up to Phase, except ELF
    public CodeGen driver( Phase phase ) { return driver(phase,null,null); }
    public CodeGen driver( Phase phase, String cpu, String callingConv ) {
        if( _phase==null )                       parse();
        if( _phase.ordinal() < phase.ordinal() ) opto();
        if( _phase.ordinal() < phase.ordinal() ) typeCheck();
        if( _phase.ordinal() < phase.ordinal() ) loopTree();
        if( _phase.ordinal() < phase.ordinal() && cpu != null ) instSelect(cpu,callingConv);
        if( _phase.ordinal() < phase.ordinal() ) GCM();
        if( _phase.ordinal() < phase.ordinal() ) localSched();
        if( _phase.ordinal() < phase.ordinal() ) regAlloc();
        if( _phase.ordinal() < phase.ordinal() ) encode();
        return this;
    }

    // Run all the phases through final ELF emission
    public CodeGen driver( String cpu, String callingConv, String obj ) throws IOException {
        return driver(Phase.Encoding,cpu,callingConv).exportELF(obj);
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

    // Compute "function indices": FIDX.
    // Each new request at the same signature gets a new FIDX.
    private final HashMap<TypeTuple,Integer> FIDXS = new HashMap<>();
    public TypeFunPtr makeFun( TypeTuple sig, Type ret ) {
        Integer i = FIDXS.get(sig);
        int fidx = i==null ? 0 : i;
        FIDXS.put(sig,fidx+1);  // Track count per sig
        assert fidx<64;         // TODO: need a larger FIDX space
        return TypeFunPtr.make((byte)2,sig,ret, 1L<<fidx );
    }
    // Signature for MAIN
    public TypeFunPtr _main = makeFun(TypeTuple.MAIN,Type.BOTTOM);
    // Reverse from a constant function pointer to the IR function being called
    public FunNode link( TypeFunPtr tfp ) {
        assert tfp.isConstant();
        return _linker.get(tfp.makeFrom(Type.BOTTOM));
    }

    // Insert linker mapping from constant function signature to the function
    // being called.
    public void link(FunNode fun) {
        _linker.put(fun.sig().makeFrom(Type.BOTTOM),fun);
    }

    // "Linker" mapping from constant TypeFunPtrs to heads of function.  These
    // TFPs all have exact single fidxs and their return is wiped to BOTTOM (so
    // the return is not part of the match).
    private final HashMap<TypeFunPtr,FunNode> _linker = new HashMap<>();

    // Parser object
    public final Parser P;

    // Parse ASCII text into Sea-of-Nodes IR
    public int _tParse;
    public CodeGen parse() {
        assert _phase == null;
        _phase = Phase.Parse;
        long t0 = System.currentTimeMillis();

        P.parse();
        _tParse = (int)(System.currentTimeMillis() - t0);
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
    public int _tOpto;
    public CodeGen opto() {
        assert _phase == Phase.Parse;
        _phase = Phase.Opto;
        long t0 = System.currentTimeMillis();

        // Pessimistic peephole optimization on a worklist
        _iter.iterate(this);
        _tOpto = (int)(System.currentTimeMillis() - t0);

        // TODO:
        // Optimistic
        // TODO:
        // loop unroll, peel, RCE, etc
        return this;
    }
    public <N extends Node> N add( N n ) { return (N)_iter.add(n); }
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
        _phase = Phase.InstSelect;

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
        _start = (StartNode)map.get(_start);
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

        // Updates forward edges only.
        n._outputs.clear();
        return x;
    }

    // Walk all machine Nodes, and set their output edges
    private void _instOuts( Node n, BitSet visit ) {
        if( visit.get(n._nid) ) return;
        visit.set(n._nid);
        for( Node in : n._inputs )
            if( in!=null ) {
                in._outputs.push(n);
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
        assert _phase.ordinal() <= Phase.InstSelect.ordinal();
        _phase = Phase.Schedule;
        long t0 = System.currentTimeMillis();

        GlobalCodeMotion.buildCFG(this);
        _tGCM = (int)(System.currentTimeMillis() - t0);
        if( show )
            System.out.println(new GraphVisualizer().generateDotOutput(_stop,null,null));
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
    // Exporting to external formats
    public CodeGen exportELF(String fname) throws IOException {
        assert _phase == Phase.Encoding;
        _phase = Phase.Export;
        if( fname == null ) new LinkMem(this).link(); // In memory patching
        else new ElfFile(this).export(fname); // External ELF file
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
        return _phase.ordinal() > Phase.Schedule.ordinal()
            ? IRPrinter._prettyPrint( this )
            : _stop.p(9999);
    }

    // Debugging helper
    public Node f(int idx) { return _stop.find(idx); }


    String printCFG() {
        if( _cfg==null ) return "no CFG";
        SB sb = new SB();
        for( CFGNode cfg : _cfg )
            IRPrinter.printLine(cfg,sb);
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
