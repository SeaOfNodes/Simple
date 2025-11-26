package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.BitSet;

public class ToFloatNode extends Node {
    public ToFloatNode(Node lhs) { super(null, lhs); }

    @Override public String label() { return "ToFloat"; }

    @Override public String glabel() { return "(flt)"; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return in(1)._print0(sb.append("(flt)"), visited);
    }

    @Override
    public Type compute() {
        if( in(1)._type == Type.NIL ) return TypeFloat.FZERO;
        if (in(1)._type instanceof TypeInteger i0 ) {
            if( i0.isHigh() ) return TypeFloat.F64.dual();
            if( i0.isConstant() )
                return TypeFloat.constant(i0.value());
        }
        return TypeFloat.F64;
    }

    @Override public Node idealize() { return null; }
    @Override Node copy(Node lhs, Node rhs) { return new ToFloatNode(lhs); }
}
