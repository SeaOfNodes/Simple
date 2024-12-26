 package com.seaofnodes.simple;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.*;

public class CodeGen {

    public enum Phase {
        Parse, Opto, TypeCheck, Schedule, RegAlloc;
    }
    public Phase _phase;

    public final String _src;
    // Compile-time known initial argument type
    public final TypeInteger _arg;

    public Parser P;
    public StartNode _start;
    public  StopNode _stop ;

    // Last created CodeGen as a global, for easier debugging prints
    public static CodeGen CODE;

    CodeGen( String src, TypeInteger arg ) { _phase = null; _src = src; _arg=arg; CODE=this; }
    public CodeGen( String src ) { this(src,TypeInteger.BOT); }

    public CodeGen parse() { return parse(false); }
    public CodeGen parse(boolean disable) {
        assert _phase == null;
        _phase = Phase.Parse;
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
        assert _phase == Phase.Opto;
        _phase = Phase.TypeCheck;

        // Type check
        String err = _stop.walk( Node::err );
        if( err != null )
            throw new RuntimeException(err);
        return this;
    }


    public CodeGen codegen() { return codegen(false); }
    public CodeGen codegen(boolean show) {
        assert _phase == Phase.TypeCheck;
        _phase = Phase.Schedule;

        // Build the loop tree, fix never-exit loops
        _start.buildLoopTree(_stop);
        if( show )
            System.out.println(new GraphVisualizer().generateDotOutput(_stop,null,null));

        // TODO:
        // loop unroll, peel, RCE, etc

        GlobalCodeMotion.buildCFG(_start,_stop);

        _phase = Phase.RegAlloc;

        return this;
    }

    // Debugging helper
    @Override public String toString() {
        return _stop.p(9999);
    }

    // Debugging helper
    public Node f(int idx) { return _stop.find(idx); }
}
