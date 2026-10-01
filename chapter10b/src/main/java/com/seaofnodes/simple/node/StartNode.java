package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.*;

/** The entry tuple: control, initial whole-memory state, and argument. */
public class StartNode extends MultiNode {
    final TypeTuple _args;

    public StartNode(Type[] args) {
        super();
        _type = _args = TypeTuple.make(args);
    }

    @Override
    public String label() { return "Start"; }

    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.p(label());
    }

    @Override public boolean isCFG() { return true; }
    @Override public boolean isMultiHead() { return true; }

    @Override
    public TypeTuple compute() { return _args; }

    @Override
    public Node idealize() { return null; }

    // No immediate dominator, and idepth==0
    @Override int idepth() { return 0; }
    @Override Node idom() { return null; }
}
