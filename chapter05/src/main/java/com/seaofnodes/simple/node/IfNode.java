package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeTuple;

public class IfNode extends MultiNode {

    public IfNode(Node ctrl, Node pred) {
        super(ctrl, pred);
    }

    @Override
    public String label() { return "If"; }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        sb.append("if( ");
        return in(1)._print0(sb).append(" )");
    }

    @Override public boolean isCFG() { return true; }
    
    @Override
    public Type compute() {
        return TypeTuple.IF;
    }

    @Override
    public Node idealize() {
        return null;
    }
}
