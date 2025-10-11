package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.BitSet;

public class DivNode extends ArithNode {
    public DivNode(Node lhs, Node rhs) { super(null, lhs, rhs); }
    @Override public Tag serialTag() { return Tag.Div; }

    @Override public String op() { return "//"; }

    @Override long doOp( long x, long y ) { return y==0 ? 0 : x / y; }
    @Override TypeInteger doOp(TypeInteger x, TypeInteger y) {
        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        // Div of 1.
        if( in(2)._type == TypeInteger.TRUE )
            return in(1);
        return super.idealize();
    }

    @Override Node copy(Node lhs, Node rhs) { return new DivNode(lhs,rhs); }
    @Override Node copyF() { return new DivFNode(null,null); }
}
