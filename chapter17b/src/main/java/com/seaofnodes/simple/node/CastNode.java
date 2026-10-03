package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.IterPeeps;


// Upcast (join) the input to a t.  Used after guard test to lift an input.
// Can also be used to make a type-assertion if ctrl is null.
public class CastNode extends Node {
    private final Type _t;
    public CastNode(Type t, Node ctrl, Node in) {
        super(ctrl, in);
        _t = t;
        setType(compute());
    }

    @Override public String label() { return "("+_t.str()+")"; }



    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.p(label()).n(in(1));
    }

    @Override
    public Type compute() {
        return in(1)._type.join(_t);
    }

    @Override
    public Node idealize() {
        return in(1)._type.isa(_t) ? in(1) : null;
    }
}
