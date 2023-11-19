package com.seaofnodes.simple.node;

public class LoopNode extends RegionNode {

    public LoopNode(Node... inputs) { super(false, inputs); }

    @Override
    public String label() { return "Loop"; }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        return sb.append("Loop").append(_nid);
    }

}
