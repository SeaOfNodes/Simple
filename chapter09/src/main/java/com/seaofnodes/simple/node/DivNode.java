package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.BitSet;

public class DivNode extends IntDataNode {
    public DivNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "Div"; }

    @Override public String glabel() { return "//"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append("/"), visited);
        return sb.append(")");
    }
  
    @Override
    public Type intCompute(TypeInteger i1, TypeInteger i2) {
        if( i1._is_con && i2._is_con )
            return i2._con == 0
                ? TypeInteger.ZERO
                : TypeInteger.constant( i1._con / i2._con );
        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        // TODO: Divide by 1
        return null;
    }
  
    @Override Node copy(Node lhs, Node rhs) { return new DivNode(lhs,rhs); }
}
