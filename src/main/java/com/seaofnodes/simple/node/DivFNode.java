package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;

import java.util.BitSet;

public class DivFNode extends ArithFNode {
    public DivFNode(Node lhs, Node rhs) { super(lhs, rhs); }

    @Override public String label() { return "DivF"; }

    @Override public String glabel() { return "/"; }

    @Override double doOp( double x, double y ) { return x/y; }

    @Override
    public Node idealize() {
        // Div of constant
        if( in(2)._type instanceof TypeFloat f && f.isConstant() )
            return new MulFNode(in(1),new ConstantNode(TypeFloat.constant(1.0/f.value())).peephole());

        return null;
    }
    @Override Node copy(Node lhs, Node rhs) { return new DivFNode(lhs,rhs); }
}
