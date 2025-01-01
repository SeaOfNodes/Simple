 package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.HashMap;

public class CodeGen {

    public enum Phase {
        Parse, Opto, TypeCheck, Schedule, LocalSched, RegAlloc;
    }
    public Phase _phase;

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

    public CodeGen parse() { return parse(false); }
    public CodeGen parse(boolean disable) {
        assert _phase == null;
        _phase = Phase.Parse;
        _linker.clear();

        P = new Parser(_src,_arg);
        Node._disablePeephole = disable;
        _stop = P.parse();
        _start = Parser.START;   // Capture global start
        P = null;                // No longer parsing
        return this;
    }

    public CodeGen opto() {
        assert _phase == Phase.Parse;
        _phase = Phase.Opto;

        // Optimization
        IterPeeps.iterate(_stop);

        // TODO:
        // Optimistic
        return this;
    }

    public CodeGen typeCheck() {
        // Demand phase Opto for cleaning up dead control flow at least,
        // required for the following GCM.  Note that peeps can be disabled,
        // but still the dead CFG will get cleaned.
        assert _phase == Phase.Opto;
        _phase = Phase.TypeCheck;

        // Type check
        Parser.ParseException err = _stop.walk( Node::err );
        if( err != null )
            throw err;
        return this;
    }


    public CodeGen GCM() { return GCM(false); }
    public CodeGen GCM( boolean show) {
        assert _phase == Phase.TypeCheck;
        _phase = Phase.Schedule;

        // Build the loop tree, fix never-exit loops
        _start.buildLoopTree(_stop);
        if( show )
            System.out.println(new GraphVisualizer().generateDotOutput(_stop,null,null));

        // TODO:
        // loop unroll, peel, RCE, etc
        GlobalCodeMotion.buildCFG(_start,_stop);
        return this;
    }

    // Local (basic block) scheduler phase, a classic list scheduler
    public CodeGen localSched() {
        assert _phase == Phase.Schedule;
        _phase = Phase.LocalSched;
        ListScheduler.sched(this);
        return this;
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
        return _stop.p(9999);
    }

    // Debugging helper
    public Node f(int idx) { return _stop.find(idx); }
}
