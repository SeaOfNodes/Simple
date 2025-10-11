package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

public class ShlNode extends ArithNode {
    public ShlNode(Parser.Lexer loc, Node lhs, Node rhs) { super(loc, lhs, rhs); }
    @Override public Tag serialTag() { return Tag.Shl; }

    @Override public String op() { return "<<"; }
    @Override public String glabel() { return "&lt;&lt;"; }

    @Override long doOp( long x, long y ) { return x << y; }
    @Override TypeInteger doOp( TypeInteger x, TypeInteger y ) {
        if( x == TypeInteger.ZERO )
            return TypeInteger.ZERO;
        if( y.isConstant() ) {
            int shf = (int)y.value();
            // If no overflow, shift endpoints
            if( !(((x._min<<shf)>>shf) != x._min ||
                  ((x._max<<shf)>>shf) != x._max ) )
                    return TypeInteger.make(x._min<<shf,x._max<<shf);
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

        return super.idealize();
    }
    @Override Node copy(Node lhs, Node rhs) { return new ShlNode(_loc,lhs,rhs); }
}
