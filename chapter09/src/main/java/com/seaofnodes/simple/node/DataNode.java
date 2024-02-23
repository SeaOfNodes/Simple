package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

public abstract class DataNode extends Node {

    DataNode(Node... nodes) { super(nodes); }
    
    // All DataNodes need to return BOT if any input is BOT, or TOP is any
    // input is TOP.  PhiNodes specifically can filter inputs and need
    // something different.
    @Override public final Type compute() {
        assert nIns() <= 3;
        Type d1 = in(1)._type;
        Type d2 = nIns() <= 2 ? null : in(2)._type;
        if( d1 == Type.BOTTOM ) return Type.BOTTOM;
        if( d2 == Type.BOTTOM ) return Type.BOTTOM;
        if( d1 == Type.TOP    ) return Type.TOP;
        if( d2 == Type.TOP    ) return Type.TOP;
        
        // Actual subclass compute
        return dataCompute(d1,d2);
    }

    // Not TOP nor BOTTOM
    public abstract Type dataCompute(Type d1, Type d2);
}
