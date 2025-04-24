package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.IterPeeps.WorkList;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.*;

public abstract class ListScheduler {

    // eXtra per-node stuff for scheduling.
    // Nodes are all block-local, and projections fold into their parent.
    private static final IdentityHashMap<Node,XSched> XS = new IdentityHashMap<>();
    private static class XSched {

        Node _n;         // Node this extra is for
        int _bcnt;       // Not-ready not-scheduled inputs
        int _rcnt;       // Ready not-scheduled inputs
        boolean _ruse;   // Node IS a "remote use", uses a other-block value
        boolean _rdef;   // Node IS a "remote def" of a value used in other block
        boolean _single; // Defines a single register, likely to conflict

        static final Ary<XSched> FREE = new Ary<>(XSched.class);
        static void alloc(CFGNode bb, Node n) {
            XSched x = FREE.pop();
            XS.put(n, (x==null ? new XSched() : x).init(bb,n));
        }
        void free() { _n = null; FREE.push(this); }

        static XSched get(Node n) { return n==null ? null : XS.get(n instanceof ProjNode && !(n.in(0) instanceof CallEndNode) ? n.in(0) : n); }

        private XSched init(CFGNode bb, Node n) {
            _n = n;
            _bcnt = 0;
            _ruse = _rdef = _single = false;

            // Count block-locals and remote inputs
            if( !(n instanceof PhiNode) )
                for( Node def : n._inputs )
                    if( def != null && def != bb ) {
                        if( def.cfg0()==bb ) _bcnt++; // Raise block-local input count
                        else if( !def.isMem() ) _ruse = true; // Remote register user
                    }
            // Count remote outputs and allowed registers
            if( n instanceof MultiNode )
                for( Node use : n._outputs )
                    computeSingleRDef(bb,use);
            else
                computeSingleRDef(bb,n);

            if( n instanceof NewNode || n instanceof CallNode )
                bb.fun()._hasCalls = true;
            return this;
        }

        private void computeSingleRDef(CFGNode bb, Node n) {
            // See if this is 2-input, and that input is single-def
            if( n instanceof MachNode mach && mach.twoAddress() != 0 ) {
                XSched xs = XS.get(n.in(mach.twoAddress()));
                if( xs != null )
                    _single = xs._single;
            }
            // Visit all outputs
            RegMaskRW rmask = !_single && n instanceof MachNode mach && mach.outregmap() != null ? mach.outregmap().copy() : null;
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
            _single |= rmask!=null && rmask.size1();
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
    }


    // List schedule every block
    public static void sched( CodeGen code ) {
        XS.clear();
        for( CFGNode bb : code._cfg )
            if( bb.blockHead() )
                local(bb,false);
    }

    // Classic list scheduler
    private static void local( CFGNode bb, boolean trace ) {
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
            int  pick = best(bb._outputs,sched,ready,trace);  // Pick best
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
        if( use!=null && use.in(0)==bb && !(use instanceof PhiNode) && XSched.get(use).decIsReady() )
            bb._outputs.set(ready++, use); // Became ready, move into ready set
            //bb._outputs.swap(ready++,use);  // Became ready, move into ready set
        return ready;
    }


    // Pick best between sched and ready.
    private static int best( Ary<Node> blk, int sched, int ready, boolean trace ) {
        int pick = sched;
        Node p = blk.at(pick);
        int score = score(p);
        if( trace ) { System.out.printf("%4d N%d ",score,p._nid); System.out.println(p); }
        for( int i=sched+1; i<ready; i++ ) {
            Node n = blk.at(i);
            int nscore = score(n);
            if( trace ) { System.out.printf("%4d N%d ",nscore,n._nid); System.out.println(n); }
            if( nscore > score )
                { score = nscore; pick = i; p = n; }
        }
        if( trace ) { System.out.printf("Pick: N%d %s\n\n",p._nid,p); }
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

        // Subtract 10 (delay) if this op forces a live range to exist that
        // cannot be resolved immediately; i.e., live count goes up.  Scale to
        // 20 if multi-def.

        // Subtract 100 if this op forces a single-def live range to exist
        // which might conflict on the same register with other live ranges.
        // Defines a single register based on def & uses, and the output is not
        // ready.  Scale to 200 for multi-def.
        CNT[1]=CNT[2]=0;
        XSched xn = XSched.get(n);
        // If defining a remote value, just generically stall alot.  Value is
        // used in a later block, can we delay until the end of this block?
        if( xn._rdef ) score = 200; // If defining a remote value, just generically stall alot
        boolean flags = false;
        if( n instanceof MultiNode ) {
            for( Node use : n._outputs )
                flags |= singleUseNotReady( use, xn._single );
        } else
            flags |= singleUseNotReady( n, xn._single );
        score +=  -10 * Math.min( CNT[1], 2 );
        score += -100 * Math.min( CNT[2], 2 );
        if( flags ) return 10;

        // Add 10 if this op ends a single-def live range, add 20 for 2 or more.
        // Scale to 100,200 if the single-def is also single-register.
        CNT[1]=CNT[2]=0;
        for( int i=1; i<n.nIns(); i++ ) {
            XSched xd = XSched.get( n.in(i) );
            if( xd != null && n.in(i).nOuts()==1 )
                CNT[xd._single || ((MachNode)n).regmap(i)!=null && ((MachNode)n).regmap(i).size1() ? 2 : 1]++;
        }
        score +=   10 * Math.min( CNT[1], 2 );
        score +=  100 * Math.min( CNT[2], 2 );

        // Nothing special
        assert 10 <= score && score <= 990;
        return score;
    }

    // Single use, and that use is not ready to fire once this one does.  If
    // true, stalling 'n' might reduce 'n's lifetime.  Stall by 1 (2 if single)
    // per not-ready.

    // If CFG user in this block, it must be last, so definitely stall
    // single-def (which stalls flags on x86,arm until the jmp).
    private static boolean singleUseNotReady( Node n, boolean single ) {
        // If n can rematerialize, assume allocator will split into private uses
        // as needed.  No impact on local scheduling.
        if( n.nOuts()>1 && n instanceof MachNode mach && mach.isClone() )
            return false;
        for( Node use : n.outs() ) {
            XSched xu = XSched.get(use);
            if( xu != null && xu._n instanceof CFGNode ) return true;
            if( xu != null && xu._bcnt > 0 )
                CNT[single ? 2 : 1]++; // Since bcnt>0 or CFG, stall until user is more ready
        }
        return false;
    }

}
