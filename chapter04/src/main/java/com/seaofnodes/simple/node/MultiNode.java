package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

public abstract class MultiNode extends Node {
    
    public MultiNode(Node... inputs) {
        super(inputs);
    }
    
    // Return the ProjNode with the idx
    abstract ProjNode proj(int idx);
}
