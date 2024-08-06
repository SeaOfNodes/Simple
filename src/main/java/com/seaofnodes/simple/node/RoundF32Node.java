package com.seaofnodes.simple.node;


import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;


public class RoundF32Node extends Node {
    public RoundF32Node(Node lhs) { super(null, lhs); }

    @Override public String label() { return "RoundF32"; }

    @Override protected String format() { return "((f32)%1)"; }

    @Override
    public Type compute() {
        if (in(1)._type instanceof TypeFloat i0 && i0.isConstant() )
            return TypeFloat.constant((float)i0.value());
        if( in(1)._type==TypeFloat.BOT ) return TypeFloat.B32;
        return in(1)._type;
    }

    @Override
    public Node idealize() {
        Node lhs = in(1);
        Type t1 = lhs._type;

        // RoundF32 of float
        if( t1 instanceof TypeFloat tf && tf._sz==32 )
            return lhs;

        return null;
    }
}
