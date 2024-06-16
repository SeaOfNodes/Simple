package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;

import java.util.BitSet;
import java.util.HashSet;
import java.util.HashMap;

// A collection of CFG utilities
public abstract class CFGUtil {


    // Arrange that the existing isCFG() Nodes form a valid CFG.  The
    // Node.use(0) is always a block tail (either IfNode or head of the
    // following block).  There are no unreachable infinite loops.
    static void buildCFG(StopNode stop) {

        fixLoops(stop);
        //throw Utils.TODO();
    }

    // Backwards walk on the CFG only, looking for unreachable code - which has
    // to be an infinite loop.  Insert a bogus never-taken exit to Stop, so the
    // loop becomes reachable.  Also, set loop nesting depth
    private static void fixLoops(StopNode stop) {
        // Backwards walk from Stop, looking for unreachable code
        HashSet<Node> unreach = new HashSet<>();
        HashMap<Node,Integer> visit = new HashMap<>();
        for( Node ret : stop._inputs )
            walkUnreach(ret,visit,unreach);
        if( unreach.isEmpty() ) return;

        // Forwards walk from unreachable, looking for loops with no exit test.
        BitSet bvisit = new BitSet();
        for( Node un : unreach )
            walkInf(un,bvisit);

        throw Utils.TODO();
    }

    // Backwards walk, looking for unreachable code, and setting loop nesting depth.
    private static void walkUnreach( Node cfg, HashMap<Node,Integer> visit, HashSet<Node> unreach ) {
        assert cfg.isCFG();
        if( visit.get(cfg) != null )
            return;             // Been there, done that
        unreach.remove(cfg);
        int d = 0;
        switch( cfg ) {
        case LoopNode loop:
            walkUnreach(loop.in(1),visit,unreach);
            loop._depth = d = visit.get(loop.in(1))+1;
            visit.put(loop, d);
            walkUnreach(loop.in(2),visit,unreach);
            return;
        case RegionNode region:
            for( int i=1; i<region.nIns(); i++ ) {
                walkUnreach(region.in(i),visit,unreach);
                d = Math.max(d,visit.get(region.in(i)));
            }
            visit.put(cfg,d);
            break;
        case StartNode start: break;
        case IfNode iff:
            for( Node proj : iff._outputs )
                if( visit.get(proj._nid)==null )
                    unreach.add(proj);
        default:                // Fall through
            walkUnreach(cfg.in(0),visit,unreach);
            d = visit.get(cfg.in(0));
            break;
        }
        visit.put(cfg,d);
    }

    // Forwards walk over previously unreachable, looking for loops with no
    // exit test.
    private static void walkInf( Node cfg, BitSet visit ) {
        assert cfg.isCFG();
        if( visit.get(cfg._nid) ) return; // Been there, done that
        visit.set(cfg._nid);
        if( cfg instanceof LoopNode loop ) {
            throw Utils.TODO();
        }
        for( Node use : cfg._outputs )
            if( use.isCFG() )
                walkInf(cfg,visit);
    }
}
