package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;

import java.util.HashMap;
import java.util.IdentityHashMap;

// Start of a single compilation unit, distinct from the outer whole-program Start.
public class StartCUNode extends StartNode {
    public final String _fname;
    public StartCUNode(StartNode start, StopCUNode stop, Type arg, String fname) { super(start,stop,arg);  _fname = fname;  }
    public StartCUNode(StartCUNode start) { super(start); _fname = start._fname; }


    @Override public CFGNode cfg0() { return (CFGNode)in(0); }

    // IDom is cfg0() and depth is always Start+1 which is 0+1 or 1
    @Override public int idepth() { return 1; }
    @Override public CFGNode idom(Node dep) { return cfg0(); }

}
