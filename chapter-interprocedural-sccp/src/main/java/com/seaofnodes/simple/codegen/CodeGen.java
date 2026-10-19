package com.seaofnodes.simple.codegen;

import com.seaofnodes.graph.GraphObserver;

import com.seaofnodes.simple.IterPeeps;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("unchecked")
public class CodeGen {
    public GraphObserver<Node> _obs;
    public static final String PORTS = "com.seaofnodes.simple.node.cpus";
    // Last created CodeGen as a global; used all over to avoid passing about a
    // "context".
    public static CodeGen CODE;

    public enum Phase {
        Parse,                  // Build ideal IR from source
        Iter,                   // Pessimistic peepholes
        Opto,                   // Optimistic interprocedural SCCP
        IterPostOpto,           // Peepholes and inlining using SCCP results
        FinalizeIR,             // Finish function cleanup and layouts
        TypeCheck,              // Check the surviving ideal graph
        LoopTree,               // Discover loops and connect nonreturning paths
        Select,                 // Select machine instructions
        Schedule,               // Global code motion
        LocalSched,             // Order instructions within blocks
        RegAlloc,               // Assign registers and stack slots
        Encoding,               // Encode instructions
        Export;                 // Link in memory or write ELF

    }
    public Phase _phase;

    // Run through target, skipping phases already completed.
    public CodeGen driver(Phase target) { return driver(target,null,null); }
    public CodeGen driver(Phase target, String cpu, String callingConv) { return driver(target,cpu,callingConv,0); }
    public CodeGen driver(Phase target, String cpu, String callingConv, int dump) {
        if( target.ordinal()>=Phase.RegAlloc.ordinal() && cpu==null && _mach==null )
            throw new IllegalArgumentException("Machine code requires a CPU target");
        if( target==Phase.Export ) throw new IllegalArgumentException("Use exportELF with an output file or null for memory linking");
        step(target, Phase.Parse, this::parse, dump);
        step(target, Phase.Iter, this::iter, dump);
        step(target, Phase.Opto, this::opto, dump);
        step(target, Phase.IterPostOpto, this::iterPostOpto, dump);
        step(target, Phase.FinalizeIR, this::finalizeIR, dump);
        step(target, Phase.TypeCheck, this::typeCheck, dump);
        step(target, Phase.LoopTree, this::loopTree, dump);
        step(target, Phase.Select, () -> instSelect(cpu,callingConv), dump);
        step(target, Phase.Schedule, this::GCM, dump);
        step(target, Phase.LocalSched, this::localSched, dump);
        step(target, Phase.RegAlloc, this::regAlloc, dump);
        step(target, Phase.Encoding, this::encode, dump);
        return this;
    }

    private void step(Phase target, Phase next, Runnable work, int dump) {
        if( next.ordinal()>target.ordinal() || (_phase!=null && _phase.ordinal()>=next.ordinal()) ) return;
        Phase prior = _phase;
        work.run();
        if( _phase!=prior && (dump & (1<<_phase.ordinal()))!=0 ) {
            if( (dump & (1<<30))!=0 ) System.err.println("After "+_phase+":");
            System.err.println(this);
        }
    }

    public final long[] _times = new long[Phase.values().length];
    private CodeGen phase(Phase next, boolean ordered, Runnable work) {
        assert ordered : "Cannot run "+next+" after "+_phase;
        _phase = next;
        long t0 = System.currentTimeMillis();
        try { work.run(); }
        finally { _times[next.ordinal()] = System.currentTimeMillis()-t0; }
        if( _obs!=null ) _obs.phase(next.name());
        return this;
    }


    // ---------------------------
    // Compilation source code
    public final String _src;
    // Compile-time known initial argument type
    public final TypeInteger _arg;

    // ---------------------------
    public CodeGen( String src ) { this(src, TypeInteger.BOT, 123L, true ); }
    public CodeGen( String src, TypeInteger arg) { this(src, arg==null ? TypeInteger.BOT : arg, 123L, true ); }

    public CodeGen( String src, TypeInteger arg, long workListSeed, boolean reset ) {
        CODE = this;
        if( reset ) Type.reset();
        _main = makeFun(TypeFunPtr.MAIN);
        _phase = null;
        _callingConv = null;
        _start = new StartNode(arg);
        _stop = new StopNode(src);
        _src = src;
        _arg = arg;
        _iter = new IterPeeps(workListSeed);
        P = new Parser(this,arg);
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

    // Start and stop; end points of the generated IR
    public StartNode _start;
    public StopNode  _stop;

    // Global Value Numbering.  Hash over opcode and inputs; hits in this table
    // are structurally equal.
    public final HashMap<Node,Node> _gvn = new HashMap<>();

    // Source of unique function indices
    private int _fidx =0;
    public TypeFunPtr makeFun( TypeFunPtr fun ) {
        int fidx = _fidx++;
        assert fidx<64;         // TODO: need a larger FIDX space
        return fun.makeFrom(fidx);
    }
    // Signature for MAIN
    public TypeFunPtr _main;
    public void setMain(FunNode main) {
        _main = main.sig();
        link(main);
    }
    // Reverse from a constant function pointer to the IR function being called.
    // Error to call with a non-constant TFP
    public FunNode link( TypeFunPtr tfp ) { return link(tfp.fidx());  }
    // Read-only diagnostic lookup; preserve stale entries.
    public FunNode _link(TypeFunPtr tfp) { return _link(tfp.fidx()); }
    public FunNode _link(int fidx) { return _linker.atX(fidx); }
    // Return the FunNode from a fidx, cleaning up dead entries.
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

    // Parser object
    public final Parser P;

    // Parse ASCII text into Sea-of-Nodes IR
    public CodeGen parse() {
        return phase(Phase.Parse, _phase==null, () -> {
            P.parse();
        });
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

    // Pessimistic peepholes reach a fixed point after parsing.
    public CodeGen iter() { return phase(Phase.Iter, _phase==Phase.Parse, () -> _iter.iterate(this)); }

    // SCCP computes types and queues changed nodes; Iter applies their rewrites.
    public CodeGen opto() { return phase(Phase.Opto, _phase==Phase.Iter, () -> Opto.opto(this)); }
    public CodeGen iterPostOpto() { return phase(Phase.IterPostOpto, _phase==Phase.Opto, () -> _iter.iterate(this)); }

    public CodeGen finalizeIR() { return phase(Phase.FinalizeIR, _phase==Phase.IterPostOpto, () -> FinalizeIR.run(this)); }
    public <N extends Node> N add( N n ) { return _iter.add(n); }
    public void addAll( Ary<Node> ary ) { _iter.addAll(ary); }

    // ---------------------------
    // Last check for bad programs
    public CodeGen typeCheck() { return phase(Phase.TypeCheck, _phase==Phase.FinalizeIR, () -> TypeCheck.run(this)); }

    // ---------------------------
    // Build the loop tree; break never-exit loops
    public CodeGen loopTree() {
        return phase(Phase.LoopTree, _phase==Phase.TypeCheck, () -> {
            // Build the loop tree, fix never-exit loops
            _start.buildLoopTree(_start,_stop);
        });
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
        if( cpu==null ) return this;
        return phase(Phase.Select, _phase==Phase.LoopTree,
                     () -> new InstSelect(this).run(cpu,callingConv,base));
    }


    // ---------------------------
    // Control Flow Graph in Reverse Post Order.
    public Ary<CFGNode> _cfg = new Ary<>(CFGNode.class);

    // Global schedule (code motion) nodes
    public CodeGen GCM() {
        return phase(Phase.Schedule, (_phase==Phase.LoopTree || _phase==Phase.Select), () -> {
            GlobalCodeMotion.buildCFG(this);

        });
    }

    // ---------------------------
    // Local (basic block) scheduler phase, a classic list scheduler
    public CodeGen localSched() {
        return phase(Phase.LocalSched, _phase==Phase.Schedule, () -> ListScheduler.sched(this));
    }


    // ---------------------------
    // Register Allocation
    public RegAlloc _regAlloc;
    public CodeGen regAlloc() {
        return phase(Phase.RegAlloc, _phase==Phase.LocalSched, () -> {
            _regAlloc = new RegAlloc(this);
            _regAlloc.regAlloc();
        });
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
        return phase(Phase.Encoding, _phase==Phase.RegAlloc, () -> {
            _encoding = new Encoding(this);
            preEncode();
            _encoding.encode();
        });
    }

    // ---------------------------
    // Exporting to external formats
    public CodeGen exportELF(String fname) throws IOException {
        assert _phase==Phase.Encoding;
        _phase=Phase.Export;
        long t0=System.currentTimeMillis();
        try {
            if( fname == null ) new LinkMem(this).link(); // In memory patching
            else new ElfFile(this).export(fname); // External ELF file
        } finally { _times[Phase.Export.ordinal()]=System.currentTimeMillis()-t0; }
        if( _obs!=null ) _obs.phase(Phase.Export.name());
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
            : IRPrinter.prettyPrint(this);
    }

    // Debugging helper
    public Node f(int idx) { return _stop.find(idx); }


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
