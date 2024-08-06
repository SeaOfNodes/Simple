package com.seaofnodes.simple.node;


import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;


public class MinusFNode extends Node {
    public MinusFNode(Node in) { super(null, in); }

    @Override public String label() { return "MinusF"; }

    @Override protected String format() { return "(-%1)"; }

    @Override
    public Type compute() {
        if (in(1)._type instanceof TypeFloat i0)
            return i0.isConstant() ? TypeFloat.constant(-i0.value()) : i0;
        return TypeFloat.BOT;
    }

    @Override
    public Node idealize() {
        // -(-x) is x
        if( in(1) instanceof MinusFNode minus )
            return minus.in(1);

        return null;
    }
}
