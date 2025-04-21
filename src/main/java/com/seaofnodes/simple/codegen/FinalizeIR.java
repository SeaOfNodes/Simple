package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.Arrays;

// Finish the ideal graph before type checking and scheduling.
abstract class FinalizeIR {
    static void run(CodeGen code) {
        // Not really a true optimistic pass, but look for unlinked functions.
        // This can be removed, which may trigger another round of pessimistic.
        // This is the point where we flip from a virtual Call Graph (any call
        // can call any function) to having a correct (but conservative) CG.

        FunNode main = code.link(code._main);
        for( int i=0; i<code._start.nOuts(); i++ ) {
            Node use = code._start.out(i);
            if( use instanceof FunNode fun &&
                fun.nIns()==2 && fun.in(1)==code._start && fun != main && !fun.hasPtr() &&
                    (fun._name==null || fun._name.startsWith("sys.")) ) {
                code.add(fun).setDef(1,Parser.XCTRL);
                code.addAll(fun._outputs);
                i--;
            }
        }
        code._iter.iterate(code);

    }
}
