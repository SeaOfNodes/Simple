package com.seaofnodes.simple.node;

public class ConstantNode extends Node {

    long _value;

    public ConstantNode(NodeIDGenerator idGenerator, long value, StartNode startNode) {
        super(idGenerator, OP_CONSTANT, startNode);
        _value = value;
    }
}
