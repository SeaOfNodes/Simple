package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.BitSet;

public class SarNode extends LogicalNode {
    public SarNode(Parser.Lexer loc, Node lhs, Node rhs) { super(loc, lhs, rhs); }

    @Override public String label() { return "Sar"; }
    @Override public String op() { return ">>"; }

    @Override public String glabel() { return "&gt;&gt;"; }

    @Override
    public Type compute() {
        Type t1 = in(1)._type, t2 = in(2)._type;
        if( t1.isHigh() || t2.isHigh() )
            return TypeInteger.TOP;
        if (t1 instanceof TypeInteger i0 &&
            t2 instanceof TypeInteger i1) {
            if( i0 == TypeInteger.ZERO )
                return TypeInteger.ZERO;
            if( i0.isConstant() && i1.isConstant() )
                return TypeInteger.constant(i0.value()>>i1.value());
            if( i1.isConstant() ) {
                int log = (int)i1.value();
                return TypeInteger.make(-1L<<(63-log),(1L<<(63-log))-1);
            }
        }
        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        Node lhs = in(1);
        Node rhs = in(2);
        Type t2 = rhs._type;

        // Sar of 0.
        if( t2.isConstant() && t2 instanceof TypeInteger i && (i.value()&63)==0 )
            return lhs;

        // TODO: x >> 3 >> (y ? 1 : 2) ==> x >> (y ? 4 : 5)

        return null;
    }
    @Override Node copy(Node lhs, Node rhs) { return new SarNode(_loc,lhs,rhs); }
}
