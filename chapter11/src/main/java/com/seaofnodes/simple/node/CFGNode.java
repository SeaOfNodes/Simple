package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.Utils;

import java.util.BitSet;
import java.util.HashSet;

/** Control Flow Graph Nodes
 *
 *  CFG nodes have a immediate dominator depth (idepth) and a loop nesting
 *  depth(loop_depth).
 *
 *  idepth is computed lazily upon first request, and is valid even in the
 *  Parser, and is used by peepholes during parsing and afterwards.
 *
 *  loop_depth is computed after optimization as part of scheduling.
 *
 */
public abstract class CFGNode extends Node {

    public CFGNode(Node... nodes) { super(nodes); }

    @Override public boolean isCFG() { return true; }

    public CFGNode cfg(int idx) { return (CFGNode)in(idx); }

    // Arrange that the existing isCFG() Nodes form a valid CFG.  The
    // Node.use(0) is always a block tail (either IfNode or head of the
    // following block).  There are no unreachable infinite loops.
    public static void buildCFG( StopNode stop ) {

        fixLoops(stop);
        schedEarly(stop);
        SCHEDULED = true;
        System.out.println(stop.p(99));
        //throw Utils.TODO();
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
            if( n instanceof LoadNode load )
                load.addAntiDeps(); // Loads can now place anti-deps
        }
    }
}
