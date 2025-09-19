package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;

import java.util.BitSet;

public class MulFNode extends ArithFNode {
    public MulFNode(Node lhs, Node rhs) { super(lhs, rhs); }

    @Override public String label() { return "MulF"; }

    @Override public String glabel() { return "*"; }

    @Override double doOp( double x, double y ) { return x*y; }

    @Override
    public Node idealize() {
        Node lhs = in(1);
        Node rhs = in(2);
        Type t1 = lhs._type;
        Type t2 = rhs._type;

        // Mul of 1.  We do not check for (1*x) because this will already
        // canonicalize to (x*1)
        if ( t2.isConstant() && t2 instanceof TypeFloat i && i.value()==1 )
            return lhs;

        // Move constants to RHS: con*arg becomes arg*con
        if ( t1.isConstant() && !t2.isConstant() )
            return swap12();

        return null;
    }
    @Override Node copy(Node lhs, Node rhs) { return new MulFNode(lhs,rhs); }
}
