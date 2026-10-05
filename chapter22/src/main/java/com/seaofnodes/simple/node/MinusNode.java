package com.seaofnodes.simple.node;


import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.Utils;


public class MinusNode extends Node {
    public MinusNode(Node in) { super(null, in); }

    @Override public String label() { return "Minus"; }

    @Override protected String format() { return "(-%1)"; }

    @Override
    public Type compute() {
        if( in(1)._type.isHigh() ) return TypeInteger.TOP;
        if( in(1)._type instanceof TypeInteger i0 &&
            i0._min != Long.MIN_VALUE && i0._max != Long.MIN_VALUE )
            return TypeInteger.make(-i0._max,-i0._min);
        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        // -(-x) is x
        if( in(1) instanceof MinusNode minus && addDep(minus.in(1))._type instanceof TypeInteger )
            return minus.in(1);

        return null;
    }
    @Override Node copyF() { return new MinusFNode(null); }
}
