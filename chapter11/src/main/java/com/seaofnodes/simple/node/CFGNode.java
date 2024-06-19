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

    // Arrange that the existing isCFG() Nodes form a valid CFG.  The
    // Node.use(0) is always a block tail (either IfNode or head of the
    // following block).  There are no unreachable infinite loops.
    public static void buildCFG( StopNode stop ) {
        fixLoops(stop);
        schedEarly(stop);
        SCHEDULED = true;
        schedLate(Parser.START);
    }

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
    CFGNode idom() { return cfg(0); }

    // Return the LCA of two idoms
    public static CFGNode idom(CFGNode lhs, CFGNode rhs) {
        while( lhs != rhs ) {
            var comp = lhs.idepth() - rhs.idepth();
            if( comp >= 0 ) lhs = lhs.idom();
            if( comp <= 0 ) rhs = rhs.idom();
        }
        return lhs;
    }

    // ------------------------------------------------------------------------

    // Loop nesting depth
    public int _loop_depth;
    // Tik-tok recursion pattern.  This method is final, and every caller does
    // this work.
    final int walkUnreach( HashSet<CFGNode> unreach ) {
        if( _loop_depth != 0 ) return _loop_depth;
        _loop_depth = _walkUnreach(unreach);
        unreach.remove(this);   // Since we reached here... Node was not unreachable
        return _loop_depth;
    }
    // Tik-tok recursion pattern; not-final; callers override this.
    int _walkUnreach( HashSet<CFGNode> unreach ) {
        return cfg(0).walkUnreach(unreach);
    }

    // ------------------------------------------------------------------------
    // Backwards walk on the CFG only, looking for unreachable code - which has
    // to be an infinite loop.  Insert a bogus never-taken exit to Stop, so the
    // loop becomes reachable.  Also, set loop nesting depth
    private static void fixLoops(StopNode stop) {
        // Backwards walk from Stop, looking for unreachable code
        HashSet<CFGNode> unreach = new HashSet<>();
        for( Node ret : stop._inputs )
            ((ReturnNode)ret).walkUnreach(unreach);
        if( unreach.isEmpty() ) return;

        // Forwards walk from unreachable, looking for loops with no exit test.
        BitSet visit = new BitSet();
        for( CFGNode cfg : unreach )
            cfg.walkInfinite(visit,stop);
        // Set loop depth on remaining graph
        unreach.clear();
        for( Node ret : stop._inputs )
            ((ReturnNode)ret).walkUnreach(unreach);
        assert unreach.isEmpty();
    }

    // Forwards walk over previously unreachable, looking for loops with no
    // exit test.
    private void walkInfinite( BitSet visit, StopNode stop ) {
        assert _loop_depth==0;
        if( visit.get(_nid) ) return; // Been there, done that
        visit.set(_nid);
        if( this instanceof LoopNode loop )
            loop.forceExit(stop);
        for( Node use : _outputs )
            if( use instanceof CFGNode cfg )
                cfg.walkInfinite(visit,stop);
    }

    // ------------------------------------------------------------------------
    private static void schedEarly(StopNode stop) {
        _schedEarly(stop, new BitSet());
    }

    // Backwards post-order pass.  Schedule all inputs first, then take the max
    // dom-depth scheduled input as this schedule.
    private static void _schedEarly(Node n, BitSet visit) {
        if( visit.get(n._nid) ) return; // Been there, done that
        visit.set(n._nid);
        for( Node def : n._inputs )
            if( def != null )
                _schedEarly(def,visit);
        // If not-pinned (e.g. constants, projections) and not-CFG
        if( !n.isCFG() && n.in(0)==null ) {
            // Check all inputs' controls
            CFGNode early = (CFGNode)n.in(1).in(0);
            for( int i=2; i<n.nIns(); i++ ) {
                CFGNode cfg = ((CFGNode)n.in(i).in(0));
                if( cfg.idepth() > early.idepth() )
                    early = cfg; // Latest/deepest input
            }
            n.setDef(0,early);  // First place this can go
        }
    }

    // ------------------------------------------------------------------------
    private static void schedLate(StartNode start) {
        CFGNode[] late = new CFGNode[Node.UID()];
        Node[] ns = new Node[Node.UID()];
        _schedLate(start,ns,late);
        for( int i=0; i<late.length; i++ )
            if( ns[i] != null )
                ns[i].setDef(0,late[i]);
    }

    private int _anti;          // Per-CFG field to help find anti-deps

    // Forwards post-order pass.  Schedule all outputs first, then draw a
    // idom-tree line from the LCA of uses to the early schedule.  Schedule is
    // legal anywhere on this line; pick the most control-dependent (largest
    // idepth) in the shallowest loop nest.
    private static void _schedLate(Node n, Node[] ns, CFGNode[] late) {
        if( late[n._nid]!=null ) return; // Been there, done that
        // These I know the late schedule of, and need to set early for loops
        if( n instanceof CFGNode cfg ) late[n._nid] = cfg.blockHead() ? cfg : cfg.cfg(0);
        if( n instanceof PhiNode phi ) late[n._nid] = phi.region();

        for( Node use : n._outputs ) {
            if( late[use._nid]!=null ) continue; // Been there, done that
            // Backedges get walked as part of the normal forwards flow
            if( isBackEdge(use,n) ) continue;
            // Walk Stores before Loads, so we can get the anti-deps right
            if( use._type instanceof TypeMem )
                _schedLate(use,ns,late);
        }
        for( Node use : n._outputs ) {
            if( late[use._nid]!=null ) continue; // Been there, done that
            // Backedges get walked as part of the normal forwards flow
            if( isBackEdge(use,n) ) continue;
            _schedLate(use,ns,late);
        }
        if( n.isPinned() ) return; // Already implicitly scheduled

        // Walk uses, gathering the LCA (Least Common Ancestor) of uses
        CFGNode lca = null;
        for( Node use : n._outputs ) {
            CFGNode cfg = use_block(n,use, late);
            assert cfg.blockHead();
            if( lca == null ) lca = cfg;
            else lca = idom(lca,cfg);
        }

        CFGNode early = (CFGNode)n.in(0);
        // Loads may need anti-dependencies
        if( n instanceof LoadNode load ) {
            // We ccould skip final-field loads here.
            // Walk LCA->early, flagging Load's block location choices
            for( CFGNode cfg=lca; cfg!=early.idom(); cfg = cfg.idom() )
                cfg._anti = load._nid;
            // Walk load->mem uses, looking for Stores causing an anti-dep
            for( Node mem : load.mem()._outputs ) {
                switch( mem ) {
                case StoreNode st:
                    lca = anti_dep(load,late[st._nid],lca,st);
                    break;
                case PhiNode phi:
                    // Repeat anti-dep for matching Phi inputs.
                    // No anti-dep edges but may raise the LCA.
                    for( int i=1; i<phi.nIns(); i++ )
                        if( phi.in(i)==load.mem() )
                            lca = anti_dep(load,phi.region().cfg(i),lca,null);
                    break;
                case LoadNode ld: break; // Loads do not cause anti-deps on other loads
                case ReturnNode ret: break; // Load must already be ahead of Return
                default: throw Utils.TODO();
                }
            }
        }

        // Walk up from the LCA to the early, looking for best.
        CFGNode best = lca;
        lca = lca.idom();       // Already found best for starting LCA
        for( ; lca != early.idom(); lca = lca.idom() )
            if( better(lca,best) )
                best = lca;
        assert !(best instanceof IfNode);
        ns  [n._nid] = n;
        late[n._nid] = best;
    }

    // Block of use.  Normally from late[] schedule, except for Phis, which go
    // to the matching Region input.
    private static CFGNode use_block(Node n, Node use, CFGNode[] late) {
        if( !(use instanceof PhiNode phi) )
            return late[use._nid];
        CFGNode found=null;
        for( int i=1; i<phi.nIns(); i++ )
            if( phi.in(i)==n )
                if( found==null ) found = phi.region().cfg(i);
                else Utils.TODO(); // Can be more than once
        assert found!=null;
        return found;
    }

    // Least loop depth first, then largest idepth
    private static boolean better( CFGNode lca, CFGNode best ) {
        return lca._loop_depth < best._loop_depth ||
                (lca.idepth() > best.idepth() || best instanceof IfNode);
    }

    private static boolean isBackEdge(Node use, Node n) {
        if( use instanceof LoopNode loop && loop.back()==n )
            return true;
        if( use instanceof PhiNode phi && phi.region() instanceof LoopNode && phi.in(2)==n )
            return true;
        return false;
    }

    //
    private static CFGNode anti_dep( LoadNode load, CFGNode stblk, CFGNode lca, Node st ) {
        CFGNode defblk = (CFGNode)load.mem().in(0);
        // Walk store blocks "reach" from its scheduled location to its earliest
        for( ; stblk != defblk; stblk = stblk.idom() ) {
            // Store and Load overlap, need anti-dependence
            if( stblk._anti==load._nid ) {
                CFGNode oldlca = lca;
                lca = idom(lca,stblk); // Raise Loads LCA
                if( oldlca != lca )    // And if something moved,
                    st.addDef(load);   // Add anti-dep as well
            }
        }
        return lca;
    }
}
