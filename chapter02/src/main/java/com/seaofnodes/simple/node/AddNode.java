package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

public class AddNode extends Node {
    public AddNode(Node lhs, Node rhs) {
        super(null, lhs, rhs);
    }

    @Override
    public Type compute() {
        Type lhs = in(1).compute();
        Type rhs = in(2).compute();
        if (lhs != null && rhs != null)
            return add(lhs, rhs);
        return null;
    }

    Type add(Type lhs, Type rhs) {
        TypeInteger l = lhs.isInt();
        TypeInteger r = rhs.isInt();
        if (l == null || r == null) {
            throw new RuntimeException("Only integer values supported at present");
        }
        long lo = l._lo + r._lo;
        long hi = l._hi + r._hi;
        if (l.isConstant() && r.isConstant()) {
            return new TypeInteger(lo, hi);
        }
        else {
            throw new RuntimeException("Only constants supported at present");
        }
    }

    @Override
    public String toString() { return "(" + in(1).toString() + "+" + in(2).toString() + ")"; }

}
