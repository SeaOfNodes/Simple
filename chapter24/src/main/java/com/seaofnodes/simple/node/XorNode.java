package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

public class XorNode extends ArithNode {
    public XorNode(Parser.Lexer loc, Node lhs, Node rhs) { super(loc, lhs, rhs); }

    @Override public String op() { return "^"; }
    @Override public String glabel() { return "^"; }

    @Override long doOp( long x, long y ) { return x ^ y; }
    @Override TypeInteger doOp( TypeInteger x, TypeInteger y ) {
        return TypeInteger.BOT;
    }

    @Override
    public Node idealize() {
        Node lhs = in(1);
        Node rhs = in(2);
        Type t1 = lhs._type;
        Type t2 = rhs._type;

        // Xor of 0.  We do not check for (0^x) because this will already
        // canonicalize to (x^0)
        if( t2.isConstant() && t2 instanceof TypeInteger i && i.value()==0 )
            return lhs;

        // Move constants to RHS: con*arg becomes arg*con
        if ( t1.isConstant() && !t2.isConstant() )
            return swap12();

        // Do we have ((x ^ (phi cons)) ^ con) ?
        // Do we have ((x ^ (phi cons)) ^ (phi cons)) ?
        // Push constant up through the phi: x ^ (phi con0^con0 con1^con1...)
        Node phicon = AddNode.phiCon(this,true);
        if( phicon!=null ) return phicon;

        return super.idealize();
    }
    @Override Node copy(Node lhs, Node rhs) { return new XorNode(_loc,lhs,rhs); }
}
