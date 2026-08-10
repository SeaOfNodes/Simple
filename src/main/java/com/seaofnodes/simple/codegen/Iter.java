package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;

// Establish a pessimistic fixed point after parsing/loading. Later Iter runs
// reuse the worklist and types produced by the preceding phase.
public abstract class Iter {
    static void run(CodeGen code) {
        assert code._globalEscapes==null;
        code._globalEscapes = new GlobalEscapeNode();
        code._start.setDef(2,code._globalEscapes);
        // Parsing queues initialized nodes and phase-sensitive peepholes.
        // Only the new escape summary needs seeding here.
        code.add(code._globalEscapes);
        code._iter.iterate(code);
        // Expensive assert.
        assert !CodeGen.expensiveAssert() || Opto.fixedPointCheck(code);
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

    // Saved types belong to a different caller context. Reset only the new
    // graph, then repair any affected bounds before peepholes consume them.
    static void imports(CodeGen code, Ary<Node> nodes) {
        for( Node n : nodes )
            if( !n.isDead() )
                n._type = n instanceof ConstantNode ? n.compute() : iterImportReset(n._type);
        code.addAll(nodes);
        repair(code);
    }

    // Loading a body or linking a call changes the graph and can widen
    // existing bounds. Repair only queued nodes and affected neighbors before
    // resuming the usual lifting peeps; this does not walk the graph.
    public static void repair(CodeGen code) {
        // Gather changes for a normal IterPeeps once the graph has its
        // monotonicity property back.
        Ary<Node> peeps = new Ary<>(Node.class);
        Node n;
        while( (n = code._iter._work.pop())!=null ) {
            if( n.isDead() ) continue;
            peeps.add(n);
            // Meet over existing and new; only drops
            Type t = n._type.meet(n.compute());
            if( t==n._type ) continue;
            n._type = t;
            code.addAll(n._outputs);
            n.moveDepsToWorklist();
        }
        code.addAll(peeps);
    }
}
