package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.AryInt;
import com.seaofnodes.simple.util.Utils;

import java.util.*;

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
        while( x.nOuts() == 1 && (y=x.uctrl())!=null && !(y instanceof CallNode) ) // Skip empty blocks
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
            if( lhs==null || rhs==null ) return null;
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
    public CFGNode loop() { return _ltree._head; }
    public int loopDepth() { return _ltree==null ? 0 : _ltree.depth(); }

    public LoopTree _ltree;
    public static class LoopTree {
        public LoopTree _par;
        public CFGNode _head;
        int _depth;
        LoopTree(CFGNode head) { _head = head; }
        @Override public String toString() { return label(_head); }
        public int depth() {
            return _depth==0 ? (_head instanceof FunNode || _par==null ? 0 : (_depth = _par.depth()+1)) : _depth;
        }
    }

    // ------------------------------------------------------------------------
    // Tag all CFG Nodes with their containing LoopNode; LoopNodes themselves
    // also refer to *their* containing LoopNode, as well as have their depth.
    // Start is a LoopNode which contains all at depth 1.
    public void buildLoopTree(Ary<FunNode> funs, StopNode stop) {
        // Walk all functions individually, building loop trees internally
        _ltree = stop._ltree = Parser.XCTRL._ltree = new LoopTree(this);
        BitSet post = CodeGen.CODE.visit();
        post.set(stop._nid);
        AryInt pres = new AryInt();
        int pre = 2;
        for( int i=0; i<funs._len; i++ ) {
            FunNode fun = funs.at(i);
            if( fun.isDead() ) {
                funs.del(i--);
            } else {
                fun.ret()._ltree = fun._ltree = new LoopTree(fun);
                pre = fun._bltWalk(pres,pre,fun,stop, post);
            }
        }
        post.clear();

        // Build a crude call-graph: walk all functions' calls recursively and
        // then treat the function LoopTree parent as the deepest called
        // function.
        var depth = new IdentityHashMap<FunNode, Integer>();
        for( FunNode fun : funs )
            fun._funWalk(this,depth);
    }

    int _bltWalk( AryInt pres, int pre, FunNode fun, StopNode stop, BitSet post ) {
        // Pre-walked?
        if( pres.atX(_nid)!=0 ) return pre;
        pres.setX(_nid,pre++);

        if( this instanceof CProjNode cprj ) cprj._pre = pre-1;
        if( this instanceof ReturnNode ) {
            post.set(_nid);
            return pre;
        }

        // Pre-walk
        for( Node use : _outputs )
            if( use instanceof CFGNode usecfg && !(use instanceof FunNode) )
                pre = usecfg._bltWalk( pres, pre, fun, stop, post );

        // Post-order work: find innermost loop
        LoopTree inner = null, ltree;
        for( Node use : _outputs ) {
            if( !(use instanceof CFGNode usecfg) || use instanceof FunNode )
                continue;
            if( usecfg._type == Type.XCONTROL ||       // Do not walk dead control
                usecfg._type == TypeTuple.IF_NEITHER ) // Nor dead IFs
                continue;
            // Child visited but not post-visited?
            if( !post.get(usecfg._nid) ) {
                // Must be a backedge to a LoopNode then
                ltree = usecfg._ltree = new LoopTree(usecfg);
            } else {
                // Take child's loop choice, which must exist
                ltree = usecfg._ltree;
                // If falling into a loop, use the target loop's parent instead
                if( ltree._head == usecfg ) {
                    if( ltree._par == null ) {
                        // This loop never had an If test choose to take its
                        // exit, i.e. it is a no-exit infinite loop.
                        ((LoopNode) ltree._head).forceExit( fun, stop );
                        ltree._par = fun._ltree;
                    }
                    ltree = ltree._par;
                }
            }
            // Sort inner loops.  The decision point is some branch far removed
            // from either loop head OR either backedge so requires pre-order
            // numbers to figure out innermost.
            if( inner == null ) { inner = ltree; continue; }
            if( inner == ltree ) continue; // No change
            int ltree_pre = pres.at(ltree._head._nid);
            int inner_pre = pres.at(inner._head._nid);
            LoopTree outer = ltree_pre > inner_pre ? inner : ltree;
            inner =          ltree_pre > inner_pre ? ltree : inner;
            inner._par = outer;
        }
        // Set selected loop
        if( inner!=null )
            _ltree = inner;
        // Tag as post-walked
        post.set(_nid);
        return pre;
    }

    int _funWalk( CFGNode start, IdentityHashMap<FunNode, Integer> depths ) {
        FunNode self = (FunNode)this;
        Integer d = depths.get(self);
        if( d!=null ) return d;
        depths.put(self,0);
        LoopTree deepest = start._ltree;
        int depth = 0;
        for( Node n : _inputs )
            if( n instanceof CallNode call ) {
                FunNode fun = call.fun();
                int dfun = fun._funWalk(start,depths);
                if( dfun > depth ) { depth = dfun; deepest = fun._ltree; }
            }
        assert _ltree._par == null;
        _ltree._par = deepest;
        depths.put(self,depth+1);
        return depth+1;
    }


    public static String label( CFGNode target ) {
        return switch(target) {
        case StartNode start -> start.label();
        case FunNode fun -> fun.label();
        case LoopNode loop -> "LOOP"+target._nid;
        default -> "L"+target._nid;
        };
    }
}
