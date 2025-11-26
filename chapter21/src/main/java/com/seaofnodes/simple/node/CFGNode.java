package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;

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
 *  Start - Block head; has constants and trailing Funs
 *  Region - Block head; has Phis.  Includes Fun and Parms.
 *  CallEnd - Block head; followed by CProj and Proj; also linked RPC Parms
 *  CProj - Block head
 *
 *  If - Block tail; followed by CProj same block
 *  Call - Block tail; followed by CallEnd and linked Funs
 *  New/intrinsic - If followed by CProj: block tail else mid-block; followed by Proj
 *
 */
public abstract class CFGNode extends Node {

    public CFGNode(Node...   nodes) { super(nodes); }
    public CFGNode(CFGNode cfg) {
        super(cfg);
        if( cfg != null ) {
            _idepth = cfg._idepth;
            _ltree = cfg._ltree;
            _pre = cfg._pre;
        }
    }

    public CFGNode cfg(int idx) { return (CFGNode)in(idx); }

    // Block head is Start, Region, CProj, but not e.g. If, Return, Stop
    public boolean blockHead() { return false; }

    // Get the one control following; error to call with more than one e.g. an
    // IfNode or other multi-way branch.
    public CFGNode uctrl() {
        CFGNode c = null;
        for( Node n : _outputs )
            if( n instanceof CFGNode cfg )
                {  assert c==null;  c = cfg; }
        return c;
    }

    // Used by the encoding / final BB layout
    public CFGNode uctrlSkipEmpty() {
        CFGNode x = this, y;
        while( x.nOuts() == 1 && (y=x.uctrl())!=null ) // Skip empty blocks
            x = y;
        return x;
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
    public int idepth() {
        return CodeGen.CODE.validIDepth(_idepth) ? _idepth : (_idepth=CodeGen.CODE.iDepthFrom(idom().idepth()));
    }

    // Return the immediate dominator of this Node and compute dom tree depth.
    public CFGNode idom(Node dep) { return cfg(0); }
    public final CFGNode idom() { return idom(null); }

    // Return the LCA of two idoms
    public CFGNode _idom(CFGNode rhs, Node dep) {
        if( rhs==null ) return this;
        CFGNode lhs = this;
        while( lhs != rhs ) {
            var comp = lhs.idepth() - rhs.idepth();
            if( comp >= 0 ) lhs = (dep==null ? lhs : dep.addDep(lhs)).idom();
            if( comp <= 0 ) rhs = (dep==null ? rhs : dep.addDep(rhs)).idom();
        }
        return lhs;
    }

    // Anti-dependence field support
    public int _anti;           // Per-CFG field to help find anti-deps

    // Find nearest enclosing FunNode
    public FunNode fun() {
        CFGNode cfg = this;
        while( !(cfg instanceof FunNode fun) )
            cfg = cfg.idom();
        return fun;
    }

    // ------------------------------------------------------------------------
    // Loop nesting
    public LoopNode loop() { return _ltree._head; }
    public int loopDepth() { return _ltree==null ? 0 : _ltree.depth(); }

    public LoopTree _ltree;
    public int _pre;            // Pre-order numbers for loop tree finding
    static class LoopTree {
        LoopTree _par;
        final LoopNode _head;
        int _depth;
        LoopTree(LoopNode head) { _head = head; }
        @Override public String toString() { return "LOOP"+_head._nid; }
        int depth() {
            return _depth==0 ? (_par==null ? 0 : (_depth = _par.depth()+1)) : _depth;
        }
    }

    // ------------------------------------------------------------------------
    // Tag all CFG Nodes with their containing LoopNode; LoopNodes themselves
    // also refer to *their* containing LoopNode, as well as have their depth.
    // Start is a LoopNode which contains all at depth 1.
    public void buildLoopTree(StopNode stop) {
        _ltree = stop._ltree = Parser.XCTRL._ltree = new LoopTree((StartNode)this);
        _bltWalk(2,null,stop, new BitSet());
    }
    int _bltWalk( int pre, FunNode fun, StopNode stop, BitSet post ) {
        // Pre-walked?
        if( _pre!=0 ) return pre;
        _pre = pre++;
        // Pre-walk
        for( Node use : _outputs )
            if( use instanceof CFGNode usecfg && !skip( usecfg ) )
                pre = usecfg._bltWalk( pre, use instanceof FunNode fuse ? fuse : fun, stop, post );

        // Post-order work: find innermost loop
        LoopTree inner = null, ltree;
        for( Node use : _outputs ) {
            if( !(use instanceof CFGNode usecfg) ) continue;
            if( skip(usecfg) ) continue;
            if( usecfg._type == Type.XCONTROL ||       // Do not walk dead control
                usecfg._type == TypeTuple.IF_NEITHER ) // Nor dead IFs
                continue;
            // Child visited but not post-visited?
            if( !post.get(usecfg._nid) ) {
                // Must be a backedge to a LoopNode then
                ltree = usecfg._ltree = new LoopTree((LoopNode)usecfg);
            } else {
                // Take child's loop choice, which must exist
                ltree = usecfg._ltree;
                // If falling into a loop, use the target loop's parent instead
                if( ltree._head == usecfg ) {
                    if( ltree._par == null )
                        // This loop never had an If test choose to take its
                        // exit, i.e. it is a no-exit infinite loop.
                        ltree._par = ltree._head.forceExit(fun,stop)._ltree;
                    ltree = ltree._par;
                }
            }
            // Sort inner loops.  The decision point is some branch far removed
            // from either loop head OR either backedge so requires pre-order
            // numbers to figure out innermost.
            if( inner == null ) { inner = ltree; continue; }
            if( inner == ltree ) continue; // No change
            LoopTree outer = ltree._head._pre > inner._head._pre ? inner : ltree;
            inner =          ltree._head._pre > inner._head._pre ? ltree : inner;
            inner._par = outer;
        }
        // Set selected loop
        if( inner!=null )
            _ltree = inner;
        // Tag as post-walked
        post.set(_nid);
        return pre;
    }

    private boolean skip(CFGNode usecfg) {
        // Only walk control users that are alive.
        // Do not walk from a Call to linked Fun's.
        return usecfg instanceof XCtrlNode ||
                (this instanceof CallNode && usecfg instanceof FunNode) ||
                (this instanceof ReturnNode && usecfg instanceof CallEndNode);
    }


    public String label( CFGNode target ) {
        return (target instanceof LoopNode ? "LOOP" : "L")+target._nid;
    }
}
