package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

/**
 * The Start node represents the start of the function.  For now, we do not
 * have any inputs to Start because our function does not yet accept
 * parameters.  When we add parameters the value of Start will be a tuple, and
 * will require Projections to extract the values.  We discuss this in detail
 * in Chapter 9: Functions and Calls.
 */
public class StartNode extends Node {

    public StartNode(/*arguments go here*/) {
        super();
    }

    @Override
    public String label() { return "Start"; }

    @Override
    StringBuilder _print1(StringBuilder sb) {
      return sb.append(label());
    }

    @Override public boolean isCFG() { return true; }

    @Override
    public Type compute() { return Type.BOTTOM; }

    @Override
    public Node idealize() { return null; }
}
