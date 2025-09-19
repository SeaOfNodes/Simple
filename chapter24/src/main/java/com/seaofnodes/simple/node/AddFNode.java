package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;

import java.util.BitSet;

public class AddFNode extends ArithFNode {
    public AddFNode(Node lhs, Node rhs) { super(lhs, rhs); }

    @Override public String label() { return "AddF"; }

    @Override public String glabel() { return "+"; }

    @Override double doOp( double x, double y ) { return x+y; }

    @Override
    public Node idealize() {
        // Add of 0.
        Type t2 = in(2)._type;
        if ( t2.isConstant() && t2 instanceof TypeFloat i && i.value()==0 )
            return in(1);

        // Move constants to RHS: con+arg becomes arg+con
        if ( in(1)._type.isConstant() && !t2.isConstant() )
            return swap12();

        return null;
    }
    @Override Node copy(Node lhs, Node rhs) { return new AddFNode(lhs,rhs); }
}
