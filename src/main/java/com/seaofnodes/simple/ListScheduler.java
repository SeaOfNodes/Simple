 package com.seaofnodes.simple;

import com.seaofnodes.simple.IterPeeps.WorkList;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.*;

public abstract class ListScheduler {

    // eXtra per-node stuff for scheduling.
    private static final IdentityHashMap<Node,XSched> XS = new IdentityHashMap<>();
    private static class XSched {
        static final Ary<XSched> FREE = new Ary<>(XSched.class);
        static void alloc(CFGNode bb, Node n) {
            XSched x = FREE.pop();
            XS.put(n, (x==null ? new XSched() : x).init(bb,n));
        }
        void free() { _n = null; FREE.push(this); }

        static XSched get(Node n) { return n==null ? null : XS.get(n); }

        private XSched init(CFGNode bb, Node n) {
            _n = n;
            _bcnt = 0;
            _ruse = _rdef = _single = false;

            // Count block-locals and remote inputs
            if( !(n instanceof PhiNode) )
                for( Node def : n._inputs )
                    if( def != null && def != bb ) {
                        if( def.cfg0()==bb ) _bcnt++; // Raise block-local input count
                        else                 _ruse = true;
                    }
            // Count remote outputs and allowed registers
            if( n instanceof MultiNode )
                for( Node use : n._inputs )
                    computeSingleRDef(bb,use);
            else
                computeSingleRDef(bb,n);
            return this;
        }

        private void computeSingleRDef(CFGNode bb, Node n) {
            RegMaskRW rmask = n instanceof MachNode mach && mach.outregmap()!=null ? mach.outregmap().copy() : null;
            for( Node use : n._outputs ) {
                // Remote use, so this is a remote def
                if( use!=null && use.cfg0()!=bb ) _rdef = true;
                // And mask between n's def mask and all n's users masks'
                if( use instanceof MachNode mach && rmask != null )
                    for( int i=1; i<use.nIns(); i++ )
                        if( use.in(i)==n )
                            rmask.and(mach.regmap(i));
            }
            // Defines in a single register
            _single = rmask!=null && rmask.size1();
        }

        // If _bcnt==0, declare ready; move user bcnts into rcnts.
        boolean isReady() {
            if( _bcnt > 0 || _rcnt > 0 ) return false;
            if( _n instanceof MultiNode )
                for( Node use : _n._outputs )
                    if( use instanceof ProjNode ) readyUp(use);
                    // anti-dep from a multireg directly
                    else _readyUp(use);
            else
                readyUp(_n);
            return true;
        }
        private static void readyUp(Node n) {
            for( Node use : n._outputs )
                _readyUp(use);
        }
        private static void _readyUp(Node use) {
            XSched xs = get(use);
            if( xs!=null && !(use instanceof PhiNode) )
                { assert xs._bcnt>0; xs._bcnt--; xs._rcnt++; }
        }

        boolean decIsReady() {
            assert _rcnt > 0;
            _rcnt--;
            return isReady();
        }

        Node _n;         // Node this extra is for
        int _bcnt;       // Not-ready not-scheduled inputs
        int _rcnt;       // Ready not-scheduled inputs
        boolean _ruse;   // Node IS a "remote use", uses a other-block value
        boolean _rdef;   // Node IS a "remote def" of a value used in other block
        boolean _single; // Defines a single register, likely to conflict
    }


    // List schedule every block
    public static void sched( CodeGen code ) {
        XS.clear();
        for( CFGNode bb : code._cfg )
            if( bb.blockHead() )
                local(bb);
    }

    // Classic list scheduler
    private static void local( CFGNode bb ) {
        assert XS.isEmpty();
        int len = bb.nOuts();

        // Count block-locals, remote inputs and outputs
        for( int i=0; i<len; i++ )
            XSched.alloc(bb,bb.out(i));

        // Nodes are ready if they are not used by other nodes in this block.
        // Move ready up front.
        int ready = 0;
        for( int i=0; i<len; i++ )
            if( XSched.get(bb.out(i)).isReady() ) // No block-local inputs?
                bb._outputs.swap(ready++,i);      // Move into ready set
        assert len==0 || ready > 0;

        // Classic list scheduler.  Behind sched is scheduled.  Between sched
        // and ready have zero counts and are ready to schedule.  Ahead of
        // ready is undefined.
        //
        // As nodes are scheduled, the nodes they use decrement their bcnts[]. Once
        // that gets to 0, all the nodes using it are scheduled, so it's ready.
        int sched = 0;
        while( sched < len ) {
            assert sched < ready;
            int  pick = best(bb._outputs,sched,ready);  // Pick best
            Node best = bb._outputs.swap(pick,sched++); // Swap best to head of ready and move into the scheduled set
            // Lower ready count of users, and since projections are ready as
            // soon as their multi is ready, lower those now too.
            for( Node use : best._outputs )
                if( use instanceof ProjNode )
                    for( Node useuse : use._outputs )
                        ready = ready(bb,useuse,ready);
                else
                    ready = ready(bb,use,ready);
        }

        // Reset for next block
        for( XSched x : XS.values() )
            x.free();
        XS.clear();
    }

    private static int ready(CFGNode bb, Node use, int ready) {
        if( use!=null && !(use instanceof PhiNode) && use.in(0)==bb && XSched.get(use).decIsReady() )
            bb._outputs.set(ready++, use); // Became ready, move into ready set
            //bb._outputs.swap(ready++,use);  // Became ready, move into ready set
        return ready;
    }


    // Pick best between sched and ready.
    private static int best( Ary<Node> blk, int sched, int ready ) {
        int pick = sched;
        int score = score(blk.at(pick));
        for( int i=sched+1; i<ready; i++ ) {
            int nscore = score(blk.at(i));
            if( nscore > score )
                { score = nscore; pick = i; }
        }
        return pick;
    }

    // Highest score wins.  Max at 1000, min at 10, except specials.
    private static final int[] CNT = new int[3];
    static int score( Node n ) {
        if( n instanceof  ProjNode     ) return 1001; // Pinned at block entry
        if( n instanceof CProjNode     ) return 1001; // Pinned at block entry
        if( n instanceof   PhiNode     ) return 1000;
        if( n instanceof CFGNode       ) return    1; // Pinned at block exit

        int score = 500;
        // Ideal nodes ignore register pressure scheduling
        if( !(n instanceof MachNode) )
            return score;

        // Register pressure local scheduling: avoid overlapping specialized
        // registers usages.

        // Subtract 10 if this op forces a live range to exist that cannot be
        // resolved immediately; i.e., live count goes up.  Scale to 20 if
        // multi-def.

        // Subtract 100 if this op forces a single-def live range to exist
        // which might conflict on the same register with other live ranges.
        // Defines a single register based on def & uses, and the output is not
        // ready.  Scale to 200 for multi-def.
        CNT[1]=CNT[2]=0;
        XSched xn = XSched.get(n);
        if( xn._rdef ) score = 200; // If defining a remote value, just generically stall alot
        if( n instanceof MultiNode ) {
            for( Node use : n._outputs )
                singleUseNotReady( use, xn._single );
        } else if( n.nOuts() == 1 )
            singleUseNotReady( n, xn._single );
        score +=  -10 * Math.min( CNT[1], 2 );
        score += -100 * Math.min( CNT[2], 2 );

        // Add 10 if this op ends a single-def live range, add 20 for 2 or more.
        // Scale to 100,200 if the single-def is also single-register.
        CNT[1]=CNT[2]=0;
        for( int i=1; i<n.nIns(); i++ ) {
            XSched xd = XSched.get( n.in(i) );
            if( xd != null && xd._n.nOuts()==1 )
                CNT[xd._single || ((MachNode)n).regmap(i)!=null && ((MachNode)n).regmap(i).size1() ? 2 : 1]++;
        }
        score +=   10 * Math.min( CNT[1], 2 );
        score +=  100 * Math.min( CNT[2], 2 );

        //// Nothing special
        //if( n instanceof Pe            ) return  500; // RISC want to go after PE
        //if( n instanceof Risc          ) return  400; // RISC want to go after PE
        assert 10 <= score && score <= 990;
        return score;
    }

    // Single use, and that use is not ready to fire once this one does.
    // If true, stalling 'n' might reduce 'n's lifetime.
    // Return 0 for false, 1 if true, 10 if also single register
    private static void singleUseNotReady( Node n, boolean single ) {
        if( n.nOuts() != 1 ) return;
        XSched xu = XSched.get(n.out(0));
        if( xu !=null && xu._bcnt==0 )
            return;
        CNT[single ? 2 : 1]++;
    }

}
