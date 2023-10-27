package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.Type;

public class StopNode extends Node {
    public StopNode(Node... inputs) {
        super(inputs);
    }

    @Override
    public String label() {
        return "Stop";
    }

    @Override
    protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        if( ret()!=null ) return p.n(ret());
        p.p("Stop[ ");
        for( Node ret : _inputs )
            p.n(ret).p(" ");
        return p.p("]");
    }

    @Override public boolean isCFG() { return true; }

    // If a single Return, return it.
    // Otherwise, null because ambiguous.
    public ReturnNode ret() {
        return nIns()==1 ? (ReturnNode)in(0) : null;
    }

    @Override
    public Type compute() {
        return Type.BOTTOM;
    }

    @Override
    public Node idealize() {
        return null;
    }

    public Node addReturn(Node node) {
        return addDef(node);
    }

}
