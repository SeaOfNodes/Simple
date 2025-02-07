package com.seaofnodes.simple.codegen;

import com.seaofnodes.graph.GraphObserver;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.*;
import com.seaofnodes.simple.type.*;
import java.util.BitSet;
import java.util.HashMap;

public class CodeGen {
    public GraphObserver<Node> _obs;

    // Inlining can insert CFG edges and invalidate cached depth ordering.
    private char _iDepthVersion;
    public char iDepthVersion() { return _iDepthVersion; }
    public void invalidateIDepthCaches() {
        assert _iDepthVersion < Character.MAX_VALUE : "Dominator cache version exceeds 65535";
        ++_iDepthVersion;
    }


    // Last created CodeGen as a global; used all over to avoid passing about a
    // "context".
    public static CodeGen CODE;

    public enum Phase {
        Parse,                  // Build ideal IR from source
        Iter,                   // Pessimistic peepholes
        TypeCheck,              // Check the surviving ideal graph
        LoopTree,               // Discover loops and connect nonreturning paths
        InstSelect,             // Select machine instructions
        Schedule,               // Global code motion
        LocalSched,             // Order instructions within blocks
        RegAlloc;               // Assign registers and stack slots

    }
    public Phase _phase;

    public static final String PORTS = "com.seaofnodes.simple.node.cpus";

    // Run through target, skipping phases already completed.
    public CodeGen driver(Phase target) { return driver(target,null,null); }
    public CodeGen driver(Phase target, String cpu, String callingConv) { return driver(target,cpu,callingConv,0); }
    public CodeGen driver(Phase target, String cpu, String callingConv, int dump) {
        if( target.ordinal()>=Phase.RegAlloc.ordinal() && cpu==null && _mach==null )
            throw new IllegalArgumentException("Machine code requires a CPU target");
        step(target, Phase.Parse, this::parse, dump);
        step(target, Phase.Iter, this::iter, dump);
        step(target, Phase.TypeCheck, this::typeCheck, dump);
        step(target, Phase.LoopTree, this::loopTree, dump);
        step(target, Phase.InstSelect, () -> instSelect(PORTS,cpu,callingConv), dump);
        step(target, Phase.Schedule, this::GCM, dump);
        step(target, Phase.LocalSched, this::localSched, dump);
        step(target, Phase.RegAlloc, this::regAlloc, dump);
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
    public <N extends Node> N add( N n ) { return (N)_iter.add(n); }
    public void addAll( Ary<Node> ary ) { _iter.addAll(ary); }


    // ---------------------------
    // Last check for bad programs
    public CodeGen typeCheck() { return phase(Phase.TypeCheck, _phase==Phase.Iter, () -> TypeCheck.run(this)); }


    // ---------------------------
    // Code generation CPU target
    public Machine _mach;
    //
    public String _callingConv;

    // Convert to target hardware nodes
    public CodeGen instSelect( String base, String cpu, String callingConv ) {
        if( cpu==null ) return this;
        return phase(Phase.InstSelect, _phase==Phase.LoopTree,
                     () -> new InstSelect(this).run(cpu,callingConv,base));
    }


    // ---------------------------
    // Control Flow Graph in RPO order.
    public Ary<CFGNode> _cfg = new Ary<>(CFGNode.class);

    // Global schedule (code motion) nodes
    public CodeGen loopTree() {
        return phase(Phase.LoopTree, _phase==Phase.TypeCheck, () -> _start.buildLoopTree(_stop));
    }

    public CodeGen GCM() {
        return phase(Phase.Schedule, (_phase==Phase.LoopTree || _phase==Phase.InstSelect), () -> {
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
    public String reg(Node n) {
        if( _phase.ordinal() >= Phase.RegAlloc.ordinal() ) {
            String s = _regAlloc.reg(n);
            if( s!=null ) return s;
        }
        return "N"+ n._nid;
    }

    // ---------------------------
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
}
