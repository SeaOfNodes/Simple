package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;

// Start of a single compilation unit, distinct from the outer whole-program Start.
public class StartCUNode extends StartNode {
    public StartCUNode(StartNode start, StopCUNode stop, Type arg) { super(start,stop,arg); }
    public StartCUNode(StartCUNode start) { super(start); }
    @Override public Tag serialTag() { return Tag.StartCU; }

    @Override public CFGNode cfg0() { return (CFGNode)in(0); }

    // IDom is cfg0() and depth is always Start+1 which is 0+1 or 1
    @Override public int idepth() { return CodeGen.CODE.iDepthAt(1); }
    @Override public CFGNode idom(Node dep) { return cfg0(); }
}
