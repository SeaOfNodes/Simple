package com.seaofnodes.simple.node;

/**
 * Nodes are given unique dense ids.
 */
public class NodeIDGenerator {
    /**
     * Counter for node IDs, do not hand out NID 0; this is just for safety
     */
    private int nodeIDCounter = 1;

    public int newNodeID() {
        return nodeIDCounter++;
    }
}
