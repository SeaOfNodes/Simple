package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.BitSet;

public class SubNode extends Node {
    public SubNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "Sub"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append("-"), visited);
        return sb.append(")");
    }

    @Override
    public Type compute() {
        // Sub of same is 0
        if( in(1)==in(2) )
            return TypeInteger.ZERO;

        Type t1 = in(1)._type, t2 = in(2)._type;
        if( t1.isHigh() || t2.isHigh() )
            return TypeInteger.TOP;
        if( t1 instanceof TypeInteger i1 &&
            t2 instanceof TypeInteger i2 ) {
            if (i1.isConstant() && i2.isConstant())
                return TypeInteger.constant(i1.value()-i2.value());
        }

        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        Node lhs = in(1);
        Node rhs = in(2);
        Type t1 = lhs._type;
        Type t2 = rhs._type;

        if( t2.isConstant() && t2 instanceof TypeInteger i && i.value()==0 )
            return lhs;

        if( t1.isConstant() && t1 instanceof TypeInteger i && i.value()==0 )
            return new MinusNode(rhs);

        // x - (-y) is x+y
        if( rhs instanceof MinusNode minus )
            return new AddNode(lhs,minus.in(1));

        // (-x) - y is -(x+y)
        if( lhs instanceof MinusNode minus )
            return new MinusNode(new AddNode(minus.in(1),rhs).peephole());

        return null;
    }

    @Override Node copy(Node lhs, Node rhs) { return new SubNode(lhs,rhs); }
    @Override Node copyF() { return new SubFNode(null,null); }
}
