package com.seaofnodes.simple.node;

public class StartNode extends Node implements Control {
    public StartNode(int nid, Node... inputs) {
        super(nid, OP_START, inputs);
    }

    public StartNode(NodeIDGenerator idGenerator, Node... inputs) {
        super(idGenerator, OP_START, inputs);
    }
}
