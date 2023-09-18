package com.seaofnodes.simple.node;

/**
 * The Start node represents the start of the function. For now, we do not have any values in the Start node, this is because our function does not
 * yet accept parameters. When we add parameters, the value of the Start node will be a tuple, and will require Projection nodes to extract the values.
 * We discuss this in detail in Chapter 9: Functions and Calls.
 */
public class StartNode extends Node implements Control {

    public StartNode(NodeIDGenerator idGenerator, Node... inputs) {
        super(idGenerator, inputs);
    }
}
