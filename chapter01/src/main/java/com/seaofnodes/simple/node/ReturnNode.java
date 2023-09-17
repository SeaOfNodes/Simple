package com.seaofnodes.simple.node;

public class ReturnNode extends Node implements Control {
    public ReturnNode(int nid, Node... inputs) {
        super(nid, OP_RETURN, inputs);
    }

    public ReturnNode(NodeIDGenerator idGenerator, Node... inputs) {
        super(idGenerator, OP_RETURN, inputs);
    }
}
