package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMemPtr;


public class NewNode extends Node {

    TypeMemPtr _ptr;

    public NewNode(TypeMemPtr ptr, Node ctrl) {
        super(ctrl);
        this._ptr = ptr;
    }

    @Override
    public String label() { return "new"; }

    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.p("new ").p(_ptr._obj.str());
    }

    @Override
    public Type compute() { return _ptr; }

    @Override
    public Node idealize() { return null; }

    @Override
    boolean eq(Node n) { return this == n; }

    @Override
    int hash() { return _ptr.hashCode(); }
}
