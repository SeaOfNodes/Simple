package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;

import java.util.BitSet;

/**
 */
public abstract class CFGNode extends Node {

    public CFGNode(Node... nodes) { super(nodes); }

    @Override boolean isCFG() { return true; }


    /**
     * Immediate dominator tree depth, used to approximate a real IDOM during
     * parsing where we do not have the whole program, and also peepholes
     * change the CFG incrementally.
     * <p>
     * See {@link <a href="https://en.wikipedia.org/wiki/Dominator_(graph_theory)">...</a>}
     */
    public int _idepth;
    int idepth() { return _idepth==0 ? (_idepth=idom().idepth()+1) : _idepth; }

    // Return the immediate dominator of this Node and compute dom tree depth.
    Node idom() { return in(0); }

}
