package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.BitSet;

public class SubNode extends ArithNode {
    public SubNode(Node lhs, Node rhs) { super(null, lhs, rhs); }
    @Override public Tag serialTag() { return Tag.Sub; }

    @Override public String op() { return "-"; }

    @Override long doOp( long x, long y ) { return x - y; }
    @Override TypeInteger doOp(TypeInteger x, TypeInteger y) {
        // Sub of same is 0
        if( in(1)==in(2) )
            return TypeInteger.ZERO;
        // Fold ranges like {2-3} - {0-1} into {1-3}.
        if( !AddNode.overflow(x._min,-y._max) &&
            !AddNode.overflow(x._max,-y._min) &&
            y._min != Long.MIN_VALUE  )
            return TypeInteger.make(x._min-y._max,x._max-y._min);

        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        // x - (-y) is x+y
        if( in(2) instanceof MinusNode minus )
            return new AddNode(in(1),minus.in(1));

        // (-x) - y is -(x+y)
        if( in(1) instanceof MinusNode minus )
            return new MinusNode(new AddNode(minus.in(1),in(2)).peephole());

        return super.idealize();
    }

    @Override Node copy(Node lhs, Node rhs) { return new SubNode(lhs,rhs); }
    @Override Node copyF() { return new SubFNode(null,null); }
}
