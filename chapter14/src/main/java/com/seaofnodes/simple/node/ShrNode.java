package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.BitSet;

public class ShrNode extends Node {
    public ShrNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "Shr"; }

    @Override public String glabel() { return ">>>"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append(">>>"), visited);
        return sb.append(")");
    }

    @Override
    public Type compute() {
        if (in(1)._type instanceof TypeInteger i1 &&
            in(2)._type instanceof TypeInteger i2) {
            if( i1.isConstant() && i2.isConstant() )
                return TypeInteger.constant(i1.value()>>>i2.value());
            if( i1.isHigh() || i2.isHigh() )
                return TypeInteger.TOP;
            // Zero shifting a negative makes a larger positive
            // so get the endpoints correct.
            long s1 = i1._min>>>i2._min;
            long s2 = i1._max>>>i2._min;
            return TypeInteger.make(Math.min(s1,s2),Math.max(s1,s2));
        }
        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        Node lhs = in(1);
        Node rhs = in(2);
        Type t2 = rhs._type;

        // Shr of 0.
        if( t2.isConstant() && t2 instanceof TypeInteger i && (i.value()&63)==0 )
            return lhs;

        // TODO: x >>> 3 >>> (y ? 1 : 2) ==> x >>> (y ? 4 : 5)

        return null;
    }
    @Override Node copy(Node lhs, Node rhs) { return new ShrNode(lhs,rhs); }
}
