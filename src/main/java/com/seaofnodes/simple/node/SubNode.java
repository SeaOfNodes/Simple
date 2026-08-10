package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.BitSet;

public class SubNode extends ArithNode {
    @Override boolean allowFloat() { return true; }
    public SubNode(Node lhs, Node rhs) { super(null, lhs, rhs); }
    public SubNode(Node lhs, Node rhs, byte mode) { super(null, lhs, rhs,mode); }
    @Override public Tag serialTag() { return Tag.Sub; }

    @Override public String op() { return "-"; }

    @Override long   doOp( long  x,  long  y ) { return x - y; }
    @Override double doOp(double x, double y ) { return x - y; }
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
        Node lhs = in(1);
        Node rhs = in(2);
        Type t1 = lhs._type;
        Type t2 = rhs._type;

        // Keep invalid operands and unresolved numeric modes for type checking.
        if( _mode==1 && t1 instanceof TypeInteger x && t2 instanceof TypeInteger y ) {
            if( y.isConstant() && y.value()==0 ) return lhs;
            if( x.isConstant() && x.value()==0 ) return new MinusNode(rhs);
        }

        // x - (-y) is x+y
        if( rhs instanceof MinusNode minus )
            return new AddNode(lhs,minus.in(1));

        // (-x) - y is -(x+y)
        if( lhs instanceof MinusNode minus )
            return new MinusNode(new AddNode(minus.in(1),rhs).peephole());

        return super.idealize();
    }

    @Override Node copy(Node lhs, Node rhs) { return new SubNode(lhs,rhs,_mode); }
}
