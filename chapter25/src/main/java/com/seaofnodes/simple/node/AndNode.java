package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

public class AndNode extends ArithNode {
    public AndNode(Parser.Lexer loc, Node lhs, Node rhs) { super(loc, lhs, rhs); }
    @Override public Tag serialTag() { return Tag.And; }

    @Override public String op() { return "&"; }
    @Override public String glabel() { return "&"; }

    @Override long doOp( long x, long y ) { return x & y; }
    @Override TypeInteger doOp(TypeInteger x, TypeInteger y) {
        // Sharpen allowed bits if either value is narrowed
        long mask = x.mask() & y.mask();
        return mask < 0 ? TypeInteger.BOT : TypeInteger.make(0,mask);
    }

    @Override
    public Node idealize() {
        Node lhs = in(1);
        Node rhs = in(2);
        Type t1 = lhs._type;
        Type t2 = rhs._type;
        if( !(t1 instanceof TypeInteger && t2 instanceof TypeInteger t2i) )
            return null; // Malformed, e.g. (17 & 3.14)

        // And of -1.  We do not check for (-1&x) because this will already
        // canonicalize to (x&-1).  We do not check for zero, because
        // the compute() call will return a zero already.
        if( t2i.isConstant() && t2i.value()==-1 )
            return lhs;

        // Move constants to RHS: con*arg becomes arg*con
        if( t1.isConstant() && !t2.isConstant() )
            return swap12();

        // Do we have ((x & (phi cons)) & con) ?
        // Do we have ((x & (phi cons)) & (phi cons)) ?
        // Push constant up through the phi: x & (phi con0&con0 con1&con1...)
        Node phicon = AddNode.phiCon(this,true);
        if( phicon!=null )
            return phicon;

        return super.idealize();
    }
    @Override Node copy(Node lhs, Node rhs) { return new AndNode(_loc,lhs,rhs); }
}
