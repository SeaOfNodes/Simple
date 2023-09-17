package com.seaofnodes.simple.node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * All Nodes in the Sea of Nodes IR inherit from the Node class.
 * The Node class provides common functionality used by all subtypes.
 * Subtypes of Node specialize by overriding methods.
 */
public class Node {

    static final byte OP_START = 1;
    static final byte OP_REGION = 2;
    static final byte OP_RETURN = 3;
    static final byte OP_CONSTANT = 4;

    /**
     * The opcode for this node
     */
    public final byte _opCode;

    /**
     * Each node has a unique ID within a compilation context
     */
    public final int _nid;

    /**
     * Inputs to the node.
     *
     * Generally fixed length, ordered, nulls allowed, no unused trailing space.
     * The first input (offset 0) must be a Control node.
     * @see Control
     */
    public final List<Node> _inputs;

    public final List<Node> _refs = new ArrayList<>();

    protected Node(NodeIDGenerator idGenerator, byte opCode, Node ...inputs)
    {
        _nid = idGenerator.newNodeID();
        _opCode = opCode;
        _inputs = Arrays.asList(inputs);
        for (int i = 0; i < _inputs.size(); i++) {
            Node n = _inputs.get(i);
            if (n != null)
                n._refs.add(this);
        }
    }

    /**
     * Gets the ith input node
     * @param i Offset of the input node
     * @return Input node or null
     */
    public Node in(int i) { return _inputs != null ? _inputs.get(i) : null; }

    public int numInputs() { return _inputs != null ? _inputs.size() : 0; }

    /**
     * Is this a control node, all control nodes are marked with
     * the interface Control.
     */
    public boolean isControl() { return this instanceof Control; }

    /**
     * Hash is function of this nodes type, its _nid and
     * inputs, or opcode + input_nids
     */
    @Override
    public int hashCode() {
        int sum = _opCode;
        for (int i = 0; i < numInputs(); i++)
            if (in(i) != null)
                sum ^= in(i)._nid;
        return sum;
    }

    /**
     * Equals is opcode + input nids.
     * Note that nodes are compared by reference.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node n)) return false;
        if (_opCode != n._opCode) return false;
        if (numInputs() != n.numInputs()) return false;
        for (int i = 0; i < numInputs(); i++)
            if (in(i) != n.in(i))
                return false;
        return true;
    }
}
