package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

public class DivNode extends Node {
    public DivNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "Div"; }

    @Override public String glabel() { return "//"; }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        in(1)._print0(sb.append("("));
        in(2)._print0(sb.append("/"));
        return sb.append(")");
    }

    @Override
    public Type compute() {
        if (in(1)._type instanceof TypeInteger i0 &&
            in(2)._type instanceof TypeInteger i1) {
            if (i0.isConstant() && i1.isConstant())
                return i1.value() == 0
                    ? TypeInteger.ZERO
                    : TypeInteger.constant(i0.value()/i1.value());
            return i0.meet(i1);
        }
        return Type.BOTTOM;
    }

    @Override
    public Node idealize() { return null; }

    @Override Node copy(Node lhs, Node rhs) { return new DivNode(lhs,rhs); }
}
