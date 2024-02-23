package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

public abstract class IntDataNode extends DataNode {

    IntDataNode(Node... nodes) { super(nodes); }
    
    // All DataNodes need to return BOT if any input is BOT, or TOP is any
    // input is TOP.  PhiNodes specifically can filter inputs and need
    // something different.
    @Override public final Type dataCompute(Type d1, Type d2) {
        // Invariant from caller
        TypeInteger i1 = (TypeInteger)d1;
        TypeInteger i2 = (TypeInteger)d2;
        if( i1 == TypeInteger.BOT ) return TypeInteger.BOT;
        if( i2 == TypeInteger.BOT ) return TypeInteger.BOT;
        if( i1 == TypeInteger.TOP ) return TypeInteger.TOP;
        if( i2 == TypeInteger.TOP ) return TypeInteger.TOP;
        return intCompute(i1,i2);
    }

    public abstract Type intCompute(TypeInteger i1, TypeInteger i2);
}
