package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.Type;

public class RegionNode extends Node {
    public RegionNode(Node... inputs) { super(inputs); }

    @Override
    public String label() { return "Region"; }

    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.p(label()).p(_nid);
    }

    @Override public boolean isCFG() { return true; }

    @Override
    public Type compute() {
        return Type.CONTROL;
    }

    @Override
    public Node idealize() {
        return null;
    }
}
