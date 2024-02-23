package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.BitSet;

public class SubNode extends IntDataNode {
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
    public Type intCompute(TypeInteger i1, TypeInteger i2) {
        if( i1._is_con && i2._is_con )
            return TypeInteger.constant( i1._con - i2._con );
        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        // Sub of same is 0
        if( in(1)==in(2) )
            return new ConstantNode(TypeInteger.constant(0));

        return null;
    }

    @Override Node copy(Node lhs, Node rhs) { return new SubNode(lhs,rhs); }
}
