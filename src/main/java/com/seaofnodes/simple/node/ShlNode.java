package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.BitSet;

public class ShlNode extends Node {
    public ShlNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "Shl"; }

    @Override public String glabel() { return "<<"; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        in(1)._print0(sb.append("("), visited);
        in(2)._print0(sb.append("<<"), visited);
        return sb.append(")");
    }

    @Override
    public Type compute() {
        Type t1 = in(1)._type, t2 = in(2)._type;
        if( t1.isHigh() || t2.isHigh() )
            return TypeInteger.TOP;
        if (t1 instanceof TypeInteger i0 &&
            t2 instanceof TypeInteger i1 ) {
            if( i0 == TypeInteger.ZERO )
                return TypeInteger.ZERO;
            if( i0.isConstant() && i1.isConstant() )
                return TypeInteger.constant(i0.value()<<i1.value());
        }
        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        Node lhs = in(1);
        Node rhs = in(2);

        if( rhs._type instanceof TypeInteger shl && shl.isConstant() ) {
            // Shl of 0.
            if( (shl.value()&63)==0 )
                return lhs;
            // (x + c) << i  =>  (x << i) + (c << i)
            if( lhs instanceof AddNode add && add.in(2)._type instanceof TypeInteger c && c.isConstant() && c!=TypeInteger.ZERO ) {
                long sum = c.value() << shl.value();
                if( Integer.MIN_VALUE <= sum  && sum <= Integer.MAX_VALUE )
                    return new AddNode( new ShlNode(add.in(1),rhs).peephole(), Parser.con(sum) );
            }
        }

        // TODO: x << 3 << (y ? 1 : 2) ==> x << (y ? 4 : 5)

        return null;
    }
    @Override Node copy(Node lhs, Node rhs) { return new ShlNode(lhs,rhs); }
    @Override String err() {
        if( !(in(1)._type instanceof TypeInteger) ) return "Cannot '<<' " + in(1)._type;
        if( !(in(2)._type instanceof TypeInteger) ) return "Cannot '<<' " + in(2)._type;
        return null;
    }
}
