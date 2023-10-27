package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeBot;

public class IfNode extends MultiNode {

    public IfNode(Node ctrl, Node pred) {
        super(ctrl, pred);
    }

    @Override
    public String label() {
        return "If";
    }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        return sb;
    }

    @Override
    public Type compute() {
        return TypeBot.BOTTOM;
    }

    @Override
    public Node idealize() {
        return null;
    }
}
