package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.BitSet;

public class ShlNode extends LogicalNode {
    public ShlNode(Parser.Lexer loc, Node lhs, Node rhs) { super(loc, lhs, rhs); }

    @Override public String label() { return "Shl"; }
    @Override public String op() { return "<<"; }

    @Override public String glabel() { return "&lt;&lt;"; }

    @Override
    public Type compute() {
        Type t1 = in(1)._type, t2 = in(2)._type;
        if( t1.isHigh() || t2.isHigh() )
            return TypeInteger.TOP;
        if (t1 instanceof TypeInteger i0 &&
            t2 instanceof TypeInteger i1 ) {
            if( i0 == TypeInteger.ZERO )
                return TypeInteger.ZERO;
            if( i1.isConstant() ) {
                int shf = (int)i1.value();
                if( i0.isConstant() )
                    return TypeInteger.constant(i0.value()<<shf);
                // If no overflow, shift endpoints
                if( !(((i0._min<<shf)>>shf) != i0._min ||
                      ((i0._max<<shf)>>shf) != i0._max ) )
                    return TypeInteger.make(i0._min<<shf,i0._max<<shf);
            }
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
            if( lhs instanceof AddNode add && addDep(add).in(2)._type instanceof TypeInteger c && c.isConstant() ) {
                long sum = c.value() << shl.value();
                if( Integer.MIN_VALUE <= sum  && sum <= Integer.MAX_VALUE )
                    return new AddNode(new ShlNode(_loc,add.in(1),rhs).peephole(), Parser.con(sum) );
            }
        }

        // TODO: x << 3 << (y ? 1 : 2) ==> x << (y ? 4 : 5)

        return null;
    }
    @Override Node copy(Node lhs, Node rhs) { return new ShlNode(_loc,lhs,rhs); }
}
