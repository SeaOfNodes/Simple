package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.BitSet;

public class DivNode extends Node {
    public DivNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "Div"; }

    @Override public String glabel() { return "//"; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append("/"), visited);
        return sb.append(")");
    }

    @Override
    public Type compute() {
        Type t1 = in(1)._type, t2 = in(2)._type;
        if( t1.isHigh() || t2.isHigh() )
            return TypeInteger.TOP;
        if( t1 instanceof TypeInteger i1 &&
            t2 instanceof TypeInteger i2 ) {
            if (i1.isConstant() && i2.isConstant())
                return i2.value() == 0
                    ? TypeInteger.ZERO
                    : TypeInteger.constant(i1.value()/i2.value());
        }
        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        // Div of 1.
        if( in(2)._type == TypeInteger.TRUE )
            return in(1);
        return null;
    }

    @Override Node copy(Node lhs, Node rhs) { return new DivNode(lhs,rhs); }
    @Override Node copyF() { return new DivFNode(null,null); }
}
