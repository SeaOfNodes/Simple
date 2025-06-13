package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;

public abstract class ArithFNode extends Node {
    public ArithFNode(Node lhs, Node rhs) { super(null, lhs, rhs); }

    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.open().n(in(1)).p(glabel()).n(in(2)).close();
    }

    abstract double doOp(double x, double y);

    @Override
    public Type compute() {
        Type t1 = in(1)._type, t2 = in(2)._type;
        if( t1.isHigh() || t2.isHigh() )
            return Type.TOP;
        if( t1 instanceof TypeFloat f1 &&
            t2 instanceof TypeFloat f2 ) {
            if( f1.isConstant() && f2.isConstant() )
                return TypeFloat.constant(doOp(f1.value(),f2.value()));
        }
        return t1.meet(t2);
    }

}
