package com.seaofnodes.simple.node;

public class ConstantNode extends Node {

    long _value;

    public ConstantNode(int nid, long value) {
        super(nid, OP_CONSTANT);
        _value = value;
    }

    public ConstantNode(NodeIDGenerator idGenerator, long value) {
        super(idGenerator, OP_CONSTANT);
        _value = value;
    }
}
