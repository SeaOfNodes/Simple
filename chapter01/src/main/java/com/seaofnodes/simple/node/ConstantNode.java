package com.seaofnodes.simple.node;

/**
 * The Constant node represents a constant value. At present, the only constants that we allow are integer literals; therefore the Constant node contains
 * an integer value. As we add other types of constants, we will need to refactor how we represent Constant nodes.
 * <p>
 * The Constant node has no inputs. However, we set the Start node as an input to the Constant node to enable forward graph walk. This edge carries no semantic meaning,
 * it is present _solely_ to allow visitation.
 * <p>
 * The Constant node's output is the value stored in it.
 */
public class ConstantNode extends Node {

    public final long _value;

    public ConstantNode(NodeIDGenerator idGenerator, long value, StartNode startNode) {
        super(idGenerator, startNode);
        _value = value;
    }
}
