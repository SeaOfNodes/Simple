package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;

// Interference Graph
abstract public class IFG {
    // Interference Graph Support

    // Map from a Basic Block to Live-Out: {a map from a Live Range to a Def}
    private static final IdentityHashMap<CFGNode,IdentityHashMap<LRG,Node>> BBOUTS = new IdentityHashMap<>();
    private static IdentityHashMap<LRG,Node> bbLiveOut( CFGNode bb ) { return BBOUTS.get(bb); }
    static void resetBBLiveOut() {
        for( IdentityHashMap<LRG,Node> bbout : BBOUTS.values() )
            bbout.clear();
    }
    static final IdentityHashMap<LRG,Node> TMP = new IdentityHashMap<>();

    // Inteference Graph: Array of Bitsets
    private static final Ary<BitSet> IFG = new Ary<>(BitSet.class);
    static void resetIFG() {
        for( BitSet bs : IFG )
            if( bs!=null ) bs.clear();
    }

    // Set matching bit
    private static void addIFG( LRG lrg0, LRG lrg1 ) {
        short x0 = lrg0._lrg, x1 = lrg1._lrg;
        // Triangulate
        if( x0 < x1 ) _addIFG(x0,x1);
        else          _addIFG(x1,x0);
    }
    // Add lrg1 to lrg0's conflict set
    private static void _addIFG( short x0, short x1 ) {
        BitSet ifg = IFG.atX(x0);
        if( ifg==null )
            IFG.setX(x0,ifg = new BitSet());
        ifg.set(x1);
    }


    private static final Ary<CFGNode> WORK = new Ary<>(CFGNode.class);
    static void pushWork(CFGNode bb) {
        if( WORK.find(bb) == -1 )
            WORK.push(bb);
    }

    // ------------------------------------------------------------------------
    // Visit all blocks, using the live-out LRGs per-block and doing a backwards
    // walk over each block.  At the end of the walk, push the live-out sets to
    // prior blocks.  Due to loops, no single visitation order suffices.  This
    // problem can be fairly efficiently solved with a proper LIVE calculation
    // and bitsets.  In the interests of simplicity (and assumption of smaller
    // programs) I am skipping LIVE and directly computing it during the IFG
    // building.

    // Start by setting the live-out of the exit block (to empty), and putting
    // it on a worklist.

    // - Pull from worklist a block who's live-out has changed
    // - Walk backwards, adding interferences and computing live.
    // - At block head, "push" the new live-out to prior blocks.
    // - - If they pick up new live-outs, put them on the worklist.

    public static boolean build( int round, RegAlloc alloc ) {
        // Reset all to empty
        resetBBLiveOut();
        resetIFG();

        // Last block has nothing live out
        assert WORK.isEmpty();
        for( CFGNode bb : alloc._code._cfg )
            if( bb.blockHead())
                WORK.push( bb );

        // Process blocks until no more changes
        while( !WORK.isEmpty() )
            do_block(round,alloc,WORK.pop());

        return alloc.success();
    }

    // Walk one block backwards, compute live-in from live-out, and build IFG
    static void do_block( int round, RegAlloc alloc, CFGNode bb ) {
        assert bb.blockHead();
        IdentityHashMap<LRG,Node> live_out = BBOUTS.get(bb);
        TMP.clear();
        if( live_out != null )
            TMP.putAll(live_out);   // Copy bits to temp

        // A backwards walk over instructions in the basic block
        for( int inum = bb.nOuts()-1; inum >= 0; inum-- ) {
            Node n = bb.out(inum);
            if( n instanceof PhiNode ) continue;
            // In a backwards walk, proj users come before the node itself
            if( n instanceof MultiNode )
                for( Node proj : n.outs() )
                    if( proj instanceof ProjNode )
                        do_node(alloc,proj);
            if( n instanceof MachNode )
                do_node(alloc,n);
        }

        // Push live-sets backwards to priors in CFG.
        if( bb.nIns() > 2 )
            for( int i=1; i<bb.nIns(); i++ )
                mergeLiveOut(alloc,bb,i);
        else
            mergeLiveOut(alloc,bb,0);
    }

    private static void do_node(RegAlloc alloc, Node n) {
        // Defining means killing live LRG
        LRG lrg = alloc.lrg(n);
        if( lrg != null ) {
            // Check for def-side self-conflict live ranges.  These must split,
            // and only happens during the first round a particular LRG splits.
            selfConflict(alloc,n,lrg);
            TMP.remove(lrg);    // Kill def
        }

        // A copy does not define a new value, and the src and dst can use the
        // same register.  Remove the input from TMP/liveout set before
        // interfering.
        if( n instanceof MachNode m && m.isSplit() )
            TMP.remove(alloc.lrg(n.in(1))); // Kill spill-use

        // Interfere n with all live
        if( lrg!=null ) {
            // Single-register defines *must* have their register; other live
            // ranges *must* avoid this register, so instead of interfering we
            // remove the single register from the other rmask.
            RegMask mustMask = null;
            boolean mustDef = n instanceof MachNode m && (mustMask=m.outregmap()).size1(); // Must define this register
            // Interfere n with all live
            for( LRG tlrg : TMP.keySet() ) {
                assert !tlrg.unified();
                // Skip self
                if( lrg != tlrg &&
                    // And register sets overlap
                    lrg._mask.overlap(tlrg._mask) )
                    // Then tlrg and lrg interfere.
                    // If lrg *must* get its register, make tlrg skip this register.
                    if( mustDef ) {
                        if( !tlrg.clr(mustMask.firstColor()) )
                            alloc.fail(tlrg);
                    } else addIFG(lrg,tlrg); // Add interference
            }
        }


        // Check for self-conflict live ranges.  These must split, and only
        // happens during the first round when a particular LRG splits.
        // Also record use-side spills for biased coloring.
        // Then make all inputs live.
        for( int i=1; i<n.nIns(); i++ ) {
            Node def = n.in(i);
            if( def==null ) continue;
            LRG lrg1 = alloc.lrg(def);
            if( lrg1==null ) continue; // Anti-dependence, etc, no register

            // Check use-side for self-conflict; if so we MUST split
            selfConflict(alloc,def,lrg1);

            // Look for a must-use single register conflicting with some other must-def.
            if( n instanceof MachNode m ) {
                RegMask ni_mask = m.regmap(i);
                if( ni_mask.size1() ) { // Must-use single register
                    // Search all current live
                    for( LRG tlrg : TMP.keySet() ) {
                        assert !tlrg.unified();
                        Node live = TMP.get(tlrg);
                        if( live != def && live instanceof MachNode lmach && lmach.outregmap().overlap(ni_mask) ) {
                            // Look at live value and see if it must-def same register.
                            if( lmach.outregmap().size1() ||
                                // Deny the register, since it absolutely must be used here
                                !tlrg.clr(ni_mask.firstColor()) )
                                // Then direct reg-reg conflict between use here (at n.in(i)) and def (of tlrg) there.
                                // Fail the older live range, it must move its register.
                                alloc.fail( tlrg );
                        }
                    }
                }
            }

            // All inputs live
            TMP.put(lrg1,def);
        }
    }


    // Check for self-conflict live ranges.  These must split, and only happens
    // during the first round a particular LRG splits.
    private static void selfConflict(RegAlloc alloc, Node n, LRG lrg) {
        selfConflict(alloc,n,lrg,TMP.get(lrg));
    }
    private static void selfConflict(RegAlloc alloc, Node n, LRG lrg, Node prior) {
        if( prior!=null && prior != n ) {
            lrg.selfConflict(prior);
            lrg.selfConflict(n);
            alloc.fail(lrg); // 2 unrelated values live at once same live range; self-conflict
        }
    }

    // Merge TMP into bb's live-out set; if changes put bb on WORK
    private static void mergeLiveOut( RegAlloc alloc, CFGNode priorbb, int i ) {
        CFGNode bb = priorbb.cfg(i);
        if( bb == null ) return; // Start has no prior
        if( i==0 && !(bb instanceof StartNode) ) bb = bb.cfg0();
        assert bb.blockHead();

        // Lazy get live-out set for bb
      IdentityHashMap<LRG, Node> lrgs = BBOUTS.computeIfAbsent( bb, k -> new IdentityHashMap<>() );

      for( LRG lrg : TMP.keySet() ) {
            Node def = TMP.get(lrg);
            // Effective def comes from phi input from prior block
            if( def instanceof PhiNode phi && phi.cfg0()==priorbb ) {
                assert i!=0;
                def = phi.in( i );
            }
            Node def_bb = lrgs.get(lrg);
            if( def_bb==null ) {
                lrgs.put(lrg,def);
                pushWork(bb);
            } else {
                // Alive twice with different definitions; self-conflict
                selfConflict(alloc,def,lrg,def_bb);
            }
        }
    }


    // ------------------------------------------------------------------------
    // Color the inference graph.

    // Coloring works by removing "trivial" live ranges from the IFG - those
    // live ranges with fewer neighbors than available colors (registers).
    // These are trivial because even if every neighbor takes a unique color,
    // there's at least one more available to color this live range.

    // If we hit a clique of non-trivial live ranges, we pick one to be "at
    // risk" of not-coloring.  Good choices include live ranges with a large
    // area and few hot uses.

    // Then we reverse and put-back live ranges - picking a color as we go.
    // If there's no spare color we'll have to spill this at-risk live range.

    public static boolean color(int round, RegAlloc alloc) {

        // Convert the 2-D array of bits (a 1-D array of BitSets) into an
        // adjacency matrix.
        for( int i=1; i<IFG._len; i++ ) {
            BitSet ifg = IFG.atX(i);
            if( ifg != null ) {
                LRG lrg0 = alloc._LRGS[i];
                for( int lrg = ifg.nextSetBit(0); lrg>=0; lrg=ifg.nextSetBit(lrg+1) ) {
                    LRG lrg1 = alloc._LRGS[lrg];
                    lrg0.addNeighbor(lrg1);
                    lrg1.addNeighbor(lrg0);
                }
            }
        }

        // Simplify

        // Walk all the LRGS looking for some trivial ones to start with.
        // During this pass all LRGS are broken into 3 disjoint sets:
        // - trivial, removed from IFG;           color_stack[0 to sptr]
        // - trivial, not (yet) removed from IFG; color_stack[sptr to swork]
        // - unknown;                             color_stack[work to maxlrg]
        LRG[] color_stack = Arrays.copyOf(alloc._LRGS, alloc._LRGS.length);
        // Gather trivial not-removed set.
        int sptr = 1, swork = 1;
        for( LRG lrg : alloc._LRGS )
            if( lrg!=null && lrg.lowDegree() )
                swap( color_stack, swork++, lrg._lrg );

        // Pull all lrgs from IFG, in trivial order if possible
        while( sptr < color_stack.length ) {
            // Out of trivial colorable, pick an at-risk to pull
            if( sptr==swork )
                swap(color_stack,sptr,pickRisky(color_stack,sptr));
            // Pick a trivial lrg, and (temporarily) remove from the IFG.
            LRG lrg = color_stack[sptr++];
            // If sptr was swork, then pulled an at-risk lrg
            if( sptr > swork )
                swork = sptr;

            // Walk all neighbors and remove
            if( lrg._adj != null ) {
                for( LRG nlrg : lrg._adj ) {
                    // Remove and compress out neighbor
                    if( nlrg.removeCompress(lrg) ) {
                        // Neighbor is just exactly going trivial as 'lrg' is removed from IFG
                        // Find "j" position in the color_stack
                        int jx = swork;  while( color_stack[jx] != nlrg ) jx++;
                        // Add trivial neighbor to trivial list.  Pull lrg j out of
                        // unknown set, since its now in the trivial set
                        swap(color_stack, swork++, jx);
                    }
                }
            }
        }

        // Reverse simplify (unstack the color stack), and set colors (registers) for live ranges
        while( sptr > 1 ) {
            LRG lrg = color_stack[--sptr];
            RegMaskRW rmask = lrg._mask.copy();
            // Walk neighbors and remove adjacent colors
            if( lrg._adj!=null ) {
                for( LRG nlrg : lrg._adj ) {
                    nlrg.reinsert(lrg);
                    int reg = nlrg._reg;
                    if( reg!= -1 ) // Failed neighbors do not count
                        rmask.clr(reg); // Remove neighbor from my choices
                }
            }
            // At-risk live-range did not color?
            if( rmask.isEmpty() ) {
                alloc.fail(lrg);
                lrg._reg = -1;
            } else {
                // Pick first available register
                short reg = rmask.firstColor();
                // Pick a "good" color from the choices.  Typically, biased-coloring
                // removes some over-spilling.
                if( rmask.size() > 1 ) reg = biasColor(alloc,lrg,reg,rmask);
                lrg._reg = reg; // Assign the register
            }
        }

        return alloc.success();
    }

    private static short biasColor( RegAlloc alloc, LRG lrg, short reg, RegMask mask ) {
        // Check chain of splits up the def-chain.  Take first allocated
        // register, and if its available in the mask, take it.
        Node split = lrg._splitDef;
        while( split instanceof MachNode mach && mach.isSplit() ) {
            short bias = alloc.lrg(split)._reg;
            if( bias != -1 )
                if( mask.test(bias) ) return bias; // Good bias
                else break;                        // Not allowed on the def-side; break
            split = split.in(1);
        }
        // Same check for chain of splits down the use-chain.
        split = lrg._splitUse;
        while( split instanceof MachNode mach && mach.isSplit() ) {
            short bias = alloc.lrg(split)._reg;
            if( bias != -1 )
                if( mask.test(bias) ) return bias; // Good bias
                else break;                        // Not allowed on the use-side
            split = split.out(0);
        }
        return reg;
    }

    // Pick a live range that hasn't already spilled, or has a single-def-
    // single-use that are not adjacent.
    private static int pickRisky( LRG[] color_stack, int sptr ) {
        return sptr;
    }

    private static void swap( LRG[] ary, int x, int y ) {
        LRG tmp = ary[x]; ary[x] = ary[y]; ary[y] = tmp;
    }
}
