package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

public class ShrNode extends ArithNode {
    public ShrNode(Parser.Lexer loc, Node lhs, Node rhs) { super(loc, lhs, rhs); }
    @Override public Tag serialTag() { return Tag.Shr; }

    @Override public String op() { return ">>>"; }
    @Override public String glabel() { return "&gt;&gt;&gt;"; }

    @Override long doOp( long x, long y ) { return x >>> y; }
    @Override TypeInteger doOp( TypeInteger x, TypeInteger y ) {
        return x == TypeInteger.ZERO ? x : TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        Node lhs = in(1);
        Node rhs = in(2);
        Type t2 = rhs._type;

        // Shr of 0.
        if( t2.isConstant() && t2 instanceof TypeInteger i && (i.value()&63)==0 )
            return lhs;

        // TODO: x >>> 3 >>> (y ? 1 : 2) ==> x >>> (y ? 4 : 5)

        return super.idealize();
    }
    @Override Node copy(Node lhs, Node rhs) { return new ShrNode(_loc,lhs,rhs); }
}
