package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

// Establish a pessimistic fixed point after parsing. Later Iter runs
// reuse the worklist and types produced by the preceding phase.
abstract class Iter {
    static void run(CodeGen code) {
        // Close parser-in-progress states. Seed a fresh pessimistic solve;
        // exposure sets start at FULL and can only shrink during Iter.
        code._start.walk(n -> {
            if( !(n instanceof ConstantNode) )
                n._type = iterEscapeReset(n._type);
            code.add(n);
            return null;
        });
        code._iter.iterate(code);
        // Expensive assert.
        assert !CodeGen.expensiveAssert() || Opto.fixedPointCheck(code);
    }

    private static Type iterEscapeReset(Type t) {
        if( t instanceof TypeMem mem )
            return mem._one ? mem : mem.makeFrom(XInt.FULL,XInt.FULL);
        if( t instanceof TypeTuple tt ) {
            TypeTuple rez = tt;
            for( int i=0; i<tt._types.length; i++ ) {
                Type tx = iterEscapeReset(tt._types[i]);
                if( tx != tt._types[i] ) rez = rez.makeFrom(i,tx);
            }
            return rez;
        }
        return t;
    }


}
