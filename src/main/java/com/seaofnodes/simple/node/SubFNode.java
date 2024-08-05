package com.seaofnodes.simple.node;


import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;


public class SubFNode extends Node {
    public SubFNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override public String label() { return "SubF"; }

    @Override protected String format() { return "(%1-%2)"; }

    @Override
    public Type compute() {
        if (in(1)._type instanceof TypeFloat i0 &&
            in(2)._type instanceof TypeFloat i1) {
            if (i0.isConstant() && i1.isConstant())
                return TypeFloat.constant(i0.value()-i1.value());
        }
        return in(1)._type.meet(in(2)._type);
    }

    @Override
    public Node idealize() {
        Node lhs = in(1);
        Type t2 = in(2)._type;

        // Sub of 1.
        if ( t2.isConstant() && t2 instanceof TypeFloat i && i.value()==0 )
            return lhs;

        return null;
    }
    @Override Node copy(Node lhs, Node rhs) { return new SubFNode(lhs,rhs); }
}
