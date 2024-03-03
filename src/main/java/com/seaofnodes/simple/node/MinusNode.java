package com.seaofnodes.simple.node;


import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;


public class MinusNode extends Node {
    public MinusNode(Node in) { super(null, in); }

    @Override public String label() { return "Minus"; }

    @Override protected String format() { return "(-%1)"; }

    @Override
    public Type compute() {
        if (in(1)._type instanceof TypeInteger i0)
            return i0.isConstant() ? TypeInteger.constant(-i0.value()) : i0;
        return in(1)._type==Type.TOP ? TypeInteger.TOP : TypeInteger.BOT;
    }

    @Override
    public Node idealize() { return null; }
}
