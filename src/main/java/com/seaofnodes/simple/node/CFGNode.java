package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.Utils;

import java.util.BitSet;
import java.util.HashSet;
import java.util.HashMap;

/** Control Flow Graph Nodes
 * <p>
 *  CFG nodes have a immediate dominator depth (idepth) and a loop nesting
 *  depth(loop_depth).
 * <p>
 *  idepth is computed lazily upon first request, and is valid even in the
 *  Parser, and is used by peepholes during parsing and afterward.
 * <p>
 *  loop_depth is computed after optimization as part of scheduling.
 *
 */
public abstract class CFGNode extends Node {

    public CFGNode(Node... nodes) { super(nodes); }

    @Override public boolean isCFG() { return true; }
    @Override public boolean isPinned() { return true; }

    public CFGNode cfg(int idx) { return (CFGNode)in(idx); }

    // Block head is Start, Region, CProj, but not e.g. If, Return, Stop
    public boolean blockHead() { return false; }

    // Should be exactly 1 tail from a block head
    public CFGNode blockTail() {
        assert blockHead();
        for( Node n : _outputs )
            if( n instanceof CFGNode cfg )
                return cfg;
        return null;
    }

    // ------------------------------------------------------------------------
    /**
     * Immediate dominator tree depth, used to approximate a real IDOM during
     * parsing where we do not have the whole program, and also peepholes
     * change the CFG incrementally.
     * <p>
     * See {@link <a href="https://en.wikipedia.org/wiki/Dominator_(graph_theory)">...</a>}
     */
    public int _idepth;
    public int idepth() { return _idepth==0 ? (_idepth=idom().idepth()+1) : _idepth; }

    // Return the immediate dominator of this Node and compute dom tree depth.
    public CFGNode idom(Node dep) { return cfg(0); }
    public final CFGNode idom() { return idom(null); }

    // Return the LCA of two idoms
    public CFGNode _idom(CFGNode rhs, Node dep) {
        if( rhs==null ) return this;
        CFGNode lhs = this;
        while( lhs != rhs ) {
            var comp = lhs.idepth() - rhs.idepth();
            if( comp >= 0 ) lhs = ((CFGNode)lhs.addDep(dep)).idom();
            if( comp <= 0 ) rhs = ((CFGNode)rhs.addDep(dep)).idom();
        }
        return lhs;
    }

    // Loop nesting depth
    public int _loopDepth;
    public int loopDepth() { return _loopDepth==0 ? (_loopDepth = cfg(0).loopDepth()) : _loopDepth; }

    // Anti-dependence field support
    public int _anti;           // Per-CFG field to help find anti-deps

    // ------------------------------------------------------------------------
    // Support routines for Global Code Motion

    // Tik-tok recursion pattern.  This method is final, and every caller does
    // this work.
    public final void walkUnreach( BitSet visit, HashSet<CFGNode> unreach ) {
        if( visit.get(_nid) ) return;
        visit.set(_nid);
        _walkUnreach(visit,unreach);
        unreach.remove(this); // Since we reached here... Node was not unreachable
    }

    // Tik-tok recursion pattern; not-final; callers override this.
    void _walkUnreach( BitSet visit, HashSet<CFGNode> unreach ) {
        cfg(0).walkUnreach(visit,unreach);
    }
}
