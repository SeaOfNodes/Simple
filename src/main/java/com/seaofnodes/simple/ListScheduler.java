 package com.seaofnodes.simple;

import com.seaofnodes.simple.IterPeeps.WorkList;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.*;

public abstract class ListScheduler {

    private static final BitSet VISIT = new BitSet();
    private static int[] BCNTS;
    public static void sched( CodeGen code ) {
        assert VISIT.isEmpty();
        BCNTS = new int[Node.UID()];
        walk(code._start,VISIT);
        VISIT.clear();
    }

    // Visit all CFG nodes
    static void walk( CFGNode cfg, BitSet visit ) {
        if( visit.get(cfg._nid) ) return;
        visit.set(cfg._nid);
        local(cfg);
        for( Node n : cfg._outputs )
            if( n instanceof CFGNode c )
                walk(c,visit);
    }

    // Local schedule this one CFG
    static void local( CFGNode bb ) {
        if( !bb.blockHead() || bb instanceof StopNode ) return;
        Node[] uses = bb._outputs._es;
        int ulen = bb._outputs._len;

        // Count block-locals
        for( int i=0; i<ulen; i++ ) {
            Node use = uses[i];
            if( use.in(0)==bb && !(use instanceof PhiNode) ) {
                // Normal basic block member: in(0)==bb and not a Phi
                for( Node def : use._inputs )
                    // Has defs from same block?  Then block-local inputs
                    if( def != null && def.cfg0()==bb ) // Same block?
                        BCNTS[use._nid]++;     // Raise block-local input count
            }
        }

        // Nodes are ready if they are not used by other nodes in this block.
        // Move ready up front. We will never look at remaining nodes, later
        // nodes are found by looking at users of ready nodes.
        int ready = 0;
        for( int i=0; i<ulen; i++ )
            if( BCNTS[uses[i]._nid]==0 )   // No block-local inputs?
                uses[ready++] = uses[i];   // Move into ready set
        assert ready > 0;

        // Classic list scheduler.  Behind sched is scheduled.  Between sched
        // and ready have zero counts and are ready to schedule.  Ahead of
        // ready is undefined.
        //
        // As nodes are scheduled, the nodes they use decrement their _bcnt. Once
        // that gets to 0, all the nodes using it are scheduled, so it's ready.
        int sched = 0;
        while( sched < ulen ) {
            assert sched < ready : "Scheduling nodes that are not ready. Probably due to duplicate nodes in the user list.";
            int pick = best(uses, sched,ready); // Pick best
            Node best = uses[pick];       // Swap best to head of ready
            uses[pick] = uses[sched];
            uses[sched++] = best; // And move it into the scheduled set
            // Lower ready count of users
            for( Node use : best._outputs )
                if( use instanceof ProjNode ) {
                    for( Node useuse : use._outputs )
                        ready = ready(bb,useuse,ready);
                } else
                    ready = ready(bb, use,ready);
        }
    }

    private static int ready(CFGNode bb, Node use, int ready) {
        if( use != null && use.in(0)==bb && !(use instanceof PhiNode) ) {
            assert BCNTS[use._nid] >= 1;
            if( --BCNTS[use._nid] == 0 )
                bb._outputs._es[ready++] = use; // Became ready, move into ready set
        }
        return ready;
    }


    // Pick best between sched and ready.
    private static int best( Node[] uses, int sched, int ready ) {
        int pick = sched;
        int score = score(uses[pick]);
        for( int i=sched+1; i<ready; i++ ) {
            int nscore = score(uses[i]);
            if( nscore > score )
                { score = nscore; pick = i; }
        }
        return pick;
    }

    // Highest score wins.  Max at 1000, min at 10, except specials.
    private static int score( Node n ) {
        if( n.isMultiTail()  ) return 1001; // Pinned just behind the multi-head
        if( n instanceof CFGNode ) return n instanceof ReturnNode ? 2 : 1; // Pinned at block exit

        // Nothing special
        return 500;
    }

}
