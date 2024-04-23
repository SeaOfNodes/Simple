package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.BitSet;

public class SubNode extends Node {
    public SubNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "Sub"; }

    @Override public String glabel() { return "-"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append("-"), visited);
        return sb.append(")");
    }

    @Override
    public Type compute() {
        // Sub of same is 0
        if( in(1)==in(2) )
            return TypeInteger.ZERO;

        if (in(1)._type instanceof TypeInteger i0 &&
            in(2)._type instanceof TypeInteger i1) {
            if (i0.isConstant() && i1.isConstant())
                return TypeInteger.constant(i0.value()-i1.value());
        }
        return in(1)._type.meet(in(2)._type);
    }

    @Override
    public Node idealize() {
        // x - (-y) is x+y
        if( in(2) instanceof MinusNode minus )
            return new AddNode(in(1),minus.in(1));

        // (-x) - y is -(x+y)
        if( in(1) instanceof MinusNode minus )
            return new MinusNode(new AddNode(minus.in(1),in(2)).peephole());

        return null;
    }

    @Override Node copy(Node lhs, Node rhs) { return new SubNode(lhs,rhs); }
}
