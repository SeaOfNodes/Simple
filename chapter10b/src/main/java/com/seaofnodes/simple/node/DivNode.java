package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.BitSet;

public class DivNode extends Node {
    public DivNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "Div"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append("/"), visited);
        return sb.append(")");
    }

    @Override
    public Type compute() {
        Type t1 = in(1)._type, t2 = in(2)._type;
        if( t1==Type.TOP || t1==TypeInteger.TOP ||
            t2==Type.TOP || t2==TypeInteger.TOP )
            return TypeInteger.TOP;
        if (t1 instanceof TypeInteger i0 &&
            t2 instanceof TypeInteger i1) {
            if (i0.isConstant() && i1.isConstant())
                return i1.value() == 0
                    ? TypeInteger.ZERO
                    : TypeInteger.constant(i0.value()/i1.value());
        }
        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        // Div of 1.
        if( in(2)._type.isConstant() && in(2)._type instanceof TypeInteger i && i.value()==1 )
            return in(1);
        return null;
    }

    @Override Node copy(Node lhs, Node rhs) { return new DivNode(lhs,rhs); }
}
