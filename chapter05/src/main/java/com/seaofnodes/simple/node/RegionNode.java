package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

public class RegionNode extends Node {
    public RegionNode(Node... inputs) { super(inputs); }

    @Override
    public String label() { return "Region"; }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        return sb.append("Region").append(_nid);
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
