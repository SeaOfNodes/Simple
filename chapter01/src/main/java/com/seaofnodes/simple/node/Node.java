package com.seaofnodes.simple.node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * All Nodes in the Sea of Nodes IR inherit from the Node class.
 * The Node class provides common functionality used by all subtypes.
 * Subtypes of Node specialize by overriding methods.
 */
public abstract class Node {


    /**
     * Each node has a unique dense Node ID within a compilation context
     * The ID is useful for debugging, for using as an offset in a bitvector,
     * as well as for computing equality of nodes (to be implemented later).
     */
    public final int _nid;

    /**
     * Inputs to the node. These are use-def references to Nodes.
     * <p>
     * Generally fixed length, ordered, nulls allowed, no unused trailing space.
     * The first input (offset 0) must be a Control nodes.
     * @see Control
     */
    public final List<Node> _inputs;

    /**
     * Outputs reference Nodes that are not null and
     * have this Node as an input. These nodes are users of this
     * node, thus these are def-use references to Nodes.
     * <p>
     * Note that the outputs are derived from inputs, it appears that
     * outputs are therefore a performance optimization to make it easier to
     * traverse the graph. The Sea of Nodes documentation in chapter 1 does not
     * mention the outputs list.
     */
    public final List<Node> _outputs;

    protected Node(NodeIDGenerator idGenerator, Node ...inputs)
    {
        _nid = idGenerator.newNodeID(); // allocate unique dense ID
        _inputs = Arrays.asList(inputs);
        _outputs = new ArrayList<>();   // The outputs will be populated by nodes that use this as input

        // Add this to as output on all nodes that are inputs
        for (Node n : _inputs) {
            if (n != null)
                n._outputs.add(this);
        }
    }

    /**
     * Gets the ith input node
     * @param i Offset of the input node
     * @return Input node or null
     */
    public Node in(int i) { return _inputs.get(i); }

    public int numInputs() { return _inputs.size(); }

    /**
     * Gets the ith output node
     * @param i Offset of the output node
     * @return Output node (not null)
     */
    public Node out(int i) { return _outputs.get(i); }

    public int numOutputs() { return _outputs.size(); }

    /**
     * Is this a control node, all control nodes are marked with
     * the interface Control.
     */
    public boolean isControl() { return this instanceof Control; }

    /*
     * hashCode and equals implementation to be added in later chapter.
     */
}
