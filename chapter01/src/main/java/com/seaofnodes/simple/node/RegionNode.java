package com.seaofnodes.simple.node;

public class RegionNode extends Node implements Control {
    public RegionNode(int nid, Node... inputs) {
        super(nid, OP_REGION, inputs);
    }

    public RegionNode(NodeIDGenerator idGenerator, Node... inputs) {
        super(idGenerator, OP_REGION, inputs);
    }
}
