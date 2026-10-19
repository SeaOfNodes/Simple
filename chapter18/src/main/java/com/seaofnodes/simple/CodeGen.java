package com.seaofnodes.simple;

import com.seaofnodes.graph.GraphObserver;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
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



    public enum Phase {
        Parse,                  // Build ideal IR from source
        Iter,                   // Pessimistic peepholes
        TypeCheck,              // Check the surviving ideal graph
        LoopTree,               // Discover loops and connect nonreturning paths
        Schedule,               // Global code motion
        LocalSched;             // Order instructions within blocks

    }
    public Phase _phase;

    // Run through target, skipping phases already completed.
    public CodeGen driver(Phase target) {
        step(target, Phase.Parse, this::parse);
        step(target, Phase.Iter, this::iter);
        step(target, Phase.TypeCheck, this::typeCheck);
        step(target, Phase.LoopTree, this::loopTree);
        step(target, Phase.Schedule, this::GCM);
        step(target, Phase.LocalSched, this::localSched);
        return this;
    }

    private void step(Phase target, Phase next, Runnable work) {
        if( next.ordinal()>target.ordinal() || (_phase!=null && _phase.ordinal()>=next.ordinal()) ) return;
        work.run();
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



    public final String _src;
    // Compile-time known initial argument type
    public final TypeInteger _arg;

    public Parser P;
    public StartNode _start;
    public  StopNode _stop ;


    // "Linker" mapping from constant TypeFunPtrs to heads of function.  These
    // TFPs all have exact single fidxs and their return is wiped to BOTTOM (so
    // the return is not part of the match).
    private final HashMap<TypeFunPtr,FunNode> _linker = new HashMap<>();


    // Last created CodeGen as a global, for easier debugging prints
    public static CodeGen CODE;

    CodeGen( String src, TypeInteger arg ) { _phase = null; _src = src; _arg=arg; CODE=this; }
    public CodeGen( String src ) { this(src,TypeInteger.BOT); }

    public CodeGen parse() {
        return phase(Phase.Parse, _phase==null, () -> {
            _linker.clear();

            P = new Parser(_src,_arg);
            _stop = P.parse();
            _start = Parser.START;   // Capture global start
            P = null;                // No longer parsing
        });
    }

    // Pessimistic peepholes reach a fixed point after parsing.
    public CodeGen iter() { return phase(Phase.Iter, _phase==Phase.Parse, () -> IterPeeps.iterate(_stop)); }

    public CodeGen typeCheck() { return phase(Phase.TypeCheck, _phase==Phase.Iter, () -> TypeCheck.run(this)); }


    public CodeGen loopTree() {
        return phase(Phase.LoopTree, _phase==Phase.TypeCheck, () -> _start.buildLoopTree(_stop));
    }

    public CodeGen GCM() {
        return phase(Phase.Schedule, _phase==Phase.LoopTree, () -> {
            // TODO:
            // loop unroll, peel, RCE, etc
            GlobalCodeMotion.buildCFG(_start,_stop);
        });
    }

    // Local (basic block) scheduler phase, a classic list scheduler
    public CodeGen localSched() {
        return phase(Phase.LocalSched, _phase==Phase.Schedule, () -> ListScheduler.sched(this));
    }

    // Reverse from a constant function pointer to the IR function being called
    public FunNode link( TypeFunPtr tfp ) {
        assert tfp.isConstant();
        return _linker.get(tfp.makeFrom(Type.BOTTOM));
    }

    public void link(FunNode fun) {
        _linker.put(fun.sig().makeFrom(Type.BOTTOM),fun);
    }

    // Testing shortcuts
    Node ctrl() { return _stop.ret().ctrl(); }
    Node expr() { return _stop.ret().expr(); }
    String print() { return _stop.print(); }

    // Debugging helper
    @Override public String toString() {
        return IRPrinter.prettyPrint(this);
    }

    // Debugging helper
    public Node f(int idx) { return _stop.find(idx); }
}
