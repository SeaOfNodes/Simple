package com.seaofnodes.simple.node;


import com.seaofnodes.simple.type.*;

public class AddNode extends Node {
    public AddNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "Add"; }

    @Override protected String format() { return "(%1+%2)"; }


    @Override
    public Type compute() {
        if( in(1)._type instanceof TypeInteger i0 &&
            in(2)._type instanceof TypeInteger i1 ) {
            if (i0.isConstant() && i1.isConstant())
                return TypeInteger.constant(i0.value()+i1.value());
        }
        return Type.BOTTOM;
    }

    @Override
    public Node idealize () {
        // TODO: add of 0, and many other peepholes added in Chaper04
        return null;
    }

}
