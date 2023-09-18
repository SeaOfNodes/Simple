package com.seaofnodes.simple.node;

/**
 * The Return node has two inputs. The first input is a control node. The second input is the data node that supplies the return value.
 * <p>
 * In this presentation, the Return node functions as the Stop node, since we are compiling a single unnamed function.
 * A separate Stop node is not necessary. Compiling multiple functions is covered in Chapter 9: Functions and Calls.
 * <p>
 * The output of the Return node is the value it obtained from the data node.
 */
public class ReturnNode extends Node implements Control {

    public ReturnNode(NodeIDGenerator idGenerator, Node... inputs) {
        super(idGenerator, inputs);
    }
}
