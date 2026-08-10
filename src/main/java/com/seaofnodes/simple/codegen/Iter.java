package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

// Establish a pessimistic fixed point after parsing/loading. Later Iter runs
// reuse the worklist and types produced by the preceding phase.
abstract class Iter {
    static void run(CodeGen code) {
        // The phase shift closes all parser-in-progress states and freezes
        // unknown external inputs.  Imported nodes carry post-Opto escape
        // precision, but Iter is a fresh pessimistic solve: memory escape
        // summaries must restart at FULL so newly discovered private escapes
        // can only be removed, never added.  Seed the complete graph once.
        boolean hasImports = code._externPaths != null;
        code._start.walk(n -> {
            if( !(n instanceof ConstantNode) )
                n._type = hasImports ? iterImportReset(n._type) : iterEscapeReset(n._type);
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

    private static Type iterImportReset(Type t) {
        if( t.isHigh() )
            return t.dual();
        if( t instanceof TypeMem mem )
            return TypeMem.make(mem._alias,iterImportReset(mem._t),mem._one,mem._clz,mem._final,XInt.FULL,XInt.FULL);
        if( t instanceof TypeMemPtr )
            return t.makeStorage();
        if( t instanceof TypeStruct )
            return t.makeStorage();
        if( t instanceof TypeTuple tt ) {
            TypeTuple rez = tt;
            for( int i=0; i<tt._types.length; i++ ) {
                Type tx = iterImportReset(tt._types[i]);
                if( tx != tt._types[i] ) rez = rez.makeFrom(i,tx);
            }
            return rez;
        }
        return t;
    }
}
