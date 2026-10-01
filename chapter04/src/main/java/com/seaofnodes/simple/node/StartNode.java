package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeTuple;

/**
 * The Start node represents the start of the function.  For now, we do not
 * have any inputs to Start because our function does not yet accept
 * parameters.  When we add parameters the value of Start will be a tuple, and
 * will require Projections to extract the values.  We discuss this in detail
 * in Chapter 9: Functions and Calls.
 */
public class StartNode extends MultiNode {

    final TypeTuple _args;

    public StartNode(Type[] args) {
        super();
        _args = new TypeTuple(args);
        _type = _args;
    }

    @Override
    public String label() { return "Start"; }

    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.p(label());
    }

    @Override public boolean isCFG() { return true; }

    @Override
    public TypeTuple compute() { return _args; }

    @Override
    public Node idealize() { return null; }
}
