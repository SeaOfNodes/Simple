package com.seaofnodes.simple.node;

/**
 * Nodes are given unique dense ids.
 */
public class NodeIDGenerator {
    /**
     * Counter for node IDs, do not hand out NID 0
     * (Presumably) because it is used for the control node?
     */
    private int nodeIDCounter = 1;

    public int newNodeID() {
        if (nodeIDCounter > 100000) {
            throw new RuntimeException("infinite node create loop");
        }
        return nodeIDCounter++;
    }
}
