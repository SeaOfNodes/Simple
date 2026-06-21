package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;

// Start of a single compilation unit, distinct from the outer whole-program Start.
public class StartCUNode extends StartNode {
    public StartCUNode(StartNode start, StopCUNode stop, Type arg) { super(start,stop,arg); }
    public StartCUNode(StartCUNode start) { super(start); }
    @Override public Tag serialTag() { return Tag.StartCU; }
}
