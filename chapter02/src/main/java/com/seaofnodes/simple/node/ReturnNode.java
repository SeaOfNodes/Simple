package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;

/**
 * The Return node has two inputs.  The first input is a control node and the
 * second is the data node that supplies the return value.
 * <p>
 * In this presentation, Return functions as a Stop node, since multiple <code>return</code> statements are not possible.
 * The Stop node will be introduced in Chapter 6 when we implement <code>if</code> statements.
 * <p>
 * The Return's output is the value from the data node.
 */
public class ReturnNode extends Node {

    public ReturnNode(Node ctrl, Node data) {
        super(ctrl, data);
    }

    public Node ctrl() { return in(0); }
    public Node expr() { return in(1); }
  
    @Override
    public String label() { return "Return"; }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        return expr()._print0(sb.append("return ")).append(";");
    }

    @Override public boolean isCFG() { return true; }
  
    @Override
    public Type compute() {
        return Type.BOTTOM;
    }

    @Override
    public Node idealize() { return null; }
}
