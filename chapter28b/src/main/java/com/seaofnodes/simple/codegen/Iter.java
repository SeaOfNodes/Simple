package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;

// Establish a pessimistic fixed point after parsing/loading. Later Iter runs
// reuse the worklist and types produced by the preceding phase.
public abstract class Iter {
    static void run(CodeGen code) {
        // The phase shift closes all parser-in-progress states and freezes
        // unknown external inputs.  Imported nodes carry post-Opto escape
        // precision, but Iter is a fresh pessimistic solve: memory escape
        // summaries must restart at FULL so newly discovered private escapes
        // can only be removed, never added.  Seed the complete graph once.
        boolean hasImports = code._externPaths != null;
        code._start.walk(n -> {
            // Type import can reconcile a constant's declaration.
            n._type = n instanceof ConstantNode ? n.compute()
                : hasImports ? iterImportReset(n._type) : iterEscapeReset(n._type);
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

    // Saved types belong to a different caller context. Reset only the new
    // graph, then repair any affected bounds before peepholes consume them.
    static void imports(CodeGen code, Ary<Node> nodes) {
        for( Node n : nodes )
            if( !n.isDead() )
                n._type = n instanceof ConstantNode ? n.compute() : iterImportReset(n._type);
        // Stop normalizes unknown memory into the unit's visible escapes.
        // Do not publish a transient FULL Stop merely because we reset its body.
        for( Node n : nodes )
            if( n instanceof StopCUNode && !n.isDead() ) n._type = n.compute();
        code.addAll(nodes);
        repair(code);
    }

    // Repair the monotonicity assert after loading new nodes (from a file via
    // Serialize).  Only widening types here makes it safe to resume the usual
    // lifting peeps.  The worklist already contains changed edges and their
    // neighbors.
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
