package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import java.util.BitSet;
import java.util.IdentityHashMap;

// Interference Graph
abstract public class IFG {
    // Interference Graph Support

    // Map from a Basic Block to Live-Out: {a map from a Live Range to a Def}
    private static final IdentityHashMap<CFGNode,IdentityHashMap<LRG,Node>> BBOUTS = new IdentityHashMap<>();
    static void resetBBLiveOut() {
        for( IdentityHashMap<LRG,Node> bbout : BBOUTS.values() )
            bbout.clear();
    }
    static final IdentityHashMap<LRG,Node> TMP = new IdentityHashMap<>();

    // Inteference Graph: Array of Bitsets
    static final Ary<BitSet> IFG = new Ary<>(BitSet.class);
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

        if( alloc.success() )
            convert2DAdjacency(alloc);
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
            if( n.in(0) != bb ) continue;
            // In a backwards walk, proj users come before the node itself
            if( n instanceof MultiNode )
                for( Node proj : n.outs() )
                    if( proj instanceof ProjNode )
                        do_node(alloc,proj);
            if( n instanceof MachNode )
                do_node(alloc,n);
        }

        // The bb head kills register, e.g. a CallEnd and caller-save registers
        if( bb instanceof MachNode mach )
            kills(alloc,mach);

        // Push live-sets backwards to priors in CFG.
        if( bb instanceof RegionNode )
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

        // Phis use and define the same live range, i.e. these LRGs already
        // marked conflicted, no need to mark again
        if( n instanceof PhiNode )
            return;

        // Kill any killed registers; milli-code routines like New can kill
        // will not being a CFG.
        if( n instanceof MachNode m )
            kills(alloc,m);

        // Interfere n with all live
        if( lrg!=null ) {
            // Interfere n with all live
            for( LRG tlrg : TMP.keySet() ) {
                assert tlrg.leader();
                // Skip self
                if( lrg != tlrg &&
                    // And register sets overlap
                    lrg._mask.overlap(tlrg._mask) )
                    // Then tlrg and lrg interfere.  If lrg needs the single
                    // last tlrg register at some point, either tlrg or lrg
                    // must fail.  If *n* (a subset of lrg) needs the single
                    // last tlrg register then only tlrg must fail.
                    if( ((MachNode)n).outregmap().size1() ) {
                        if( !tlrg.clr(lrg._mask.firstReg()) ) // Clear bit, no interference
                            alloc.fail(tlrg);                 // Clearing drives mask to empty
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
                if( ni_mask!=null && ni_mask.size1() ) { // Must-use single register
                    // Search all current live
                    for( LRG tlrg : TMP.keySet() ) {
                        assert tlrg.leader();
                        Node live = TMP.get(tlrg);
                        if( live != def && live instanceof MachNode lmach && lmach.outregmap().overlap(ni_mask) ) {
                            // Deny the register, since it absolutely must be used here
                            if( !tlrg.clr(ni_mask.firstReg()) )
                                // Then direct reg-reg conflict between use here (at n.in(i)) and def (of tlrg) there.
                                // Fail the older live range, it must move its register.
                                alloc.fail( tlrg );
                        }
                    }
                }
            }

            // All inputs live - except in self conflicts, where we keep the prior def alive
            // until it goes.
            TMP.put(lrg1,def);
        }
    }

    // Single-register defines *must* have their register; other live ranges
    // *must* avoid this register, so instead of interfering we remove the
    // single register from the other rmask.
    private static void kills( RegAlloc alloc, MachNode m ) {
        RegMask killMask = m.killmap();
        if( killMask==null ) return;
        // Kill registers with all live
        for( LRG tlrg : TMP.keySet() ) {
            assert tlrg.leader();
            // Always, tlrg cannot use kills
            if( tlrg._mask.overlap(killMask) ) {
                // Disallow clone-ables from killing registers.  Just fail
                // them and re-clone closer to target... so no kill.
                // Special case for Intel XOR used to zero.
                Node n = (Node)m;
                CFGNode effUseBlk = n.out(0) instanceof PhiNode phi ? phi.region().cfg(phi._inputs.find(n)) : n.out(0).cfg0();
                if( m.isClone() &&  // Must be clonable
                    (n.nOuts()>1 || // Has many users OR
                     // Only 1 user but effective use is remote block
                     effUseBlk != n.cfg0() ))
                    // Then fail the clonable; it should split or move
                    alloc.fail(alloc.lrg((Node)m));
                // Else clonable cannot move
                else if( !tlrg.sub(killMask) )
                    alloc.fail(tlrg);
            }
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
        while( !bb.blockHead() ) bb = bb.cfg0();

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


    static void convert2DAdjacency( RegAlloc alloc ) {
        // Convert the 2-D array of bits (a 1-D array of BitSets) into an
        // adjacency matrix.
        int maxlrg = alloc._LRGS.length;
        for( int i=1; i<maxlrg; i++ ) {
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
        int maxlrg = alloc._LRGS.length, nlrgs=0;
        for( int i=1; i<maxlrg; i++ )
            if( alloc._LRGS[i] != null )
                nlrgs++;

        // Simplify

        // Walk all the LRGS looking for some trivial ones to start with.
        // During this pass all LRGS are broken into 3 disjoint sets:
        // - trivial, removed from IFG;           color_stack[0 to sptr]
        // - trivial, not (yet) removed from IFG; color_stack[sptr to swork]
        // - unknown;                             color_stack[work to maxlrg]

        // Gather all not-unified (not-null); separate trivial and non-trivial set.
        int sptr = 0, swork = 0;
        LRG[] color_stack = new LRG[nlrgs];
        for( int i=0, j=0; i<maxlrg; i++ ) {
            LRG lrg = alloc._LRGS[i];
            if( lrg==null ) continue; // Unified lrgs are null here
            color_stack[j++] = lrg;
            if( lrg.lowDegree() ) swap(color_stack, swork++, j-1);
        }

        // Pull all lrgs from IFG, in trivial order if possible
        while( sptr < color_stack.length ) {
            // Swap best color up front
            pickColor(color_stack,sptr,swork);

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
        while( sptr > 0 ) {
            LRG lrg = color_stack[--sptr];
            if( lrg==null ) continue;
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
                short reg = rmask.firstReg();
                // Pick a "good" color from the choices.  Typically, biased-coloring
                // removes some over-spilling.
                if( rmask.size() > 1 ) reg = biasColor(alloc,lrg,reg,rmask);
                lrg._reg = reg; // Assign the register
            }
        }

        return alloc.success();
    }

    // Pick LRG from color stack
    private static void pickColor(LRG[] color_stack, int sptr, int swork) {
        // Out of trivial colorable, pick an at-risk to pull
        if( sptr==swork )
            swap(color_stack,sptr,pickRisky(color_stack,sptr));
        // When coloring, we'd like to give more choices; so when coloring we'd
        // like to see the single-def first (since no choices anyway), then
        // non-split related (so more live ranges get colored), then
        // split-related last, so they have more colors to bias towards.

        // Working in reverse, pick first split-related with many regs, then
        // those with some regs, then single-def.
        int bidx=sptr;
        LRG best=color_stack[bidx];
        for( int idx = sptr+1; idx < swork; idx++ )
            if( betterLRG(best,color_stack[idx]) )
                best = color_stack[bidx=idx];
        if( bidx != sptr )
            swap(color_stack,sptr,bidx); // Pick best at sptr
    }

    private static boolean betterLRG( LRG best, LRG lrg ) {
        // If single-def varies, keep the not-single-def
        if( best.size1() != lrg.size1() )
            return best.size1();
        // If hasSplit varies, keep the hasSplit
        if( best.hasSplit() != lrg.hasSplit() )
            return lrg.hasSplit();
        // Keep large register count
        return best.size() < lrg.size();
    }

    // Pick a live range that hasn't already spilled, or has a single-def-
    // single-use that are not adjacent.
    private static int pickRisky( LRG[] color_stack, int sptr ) {
        int best=sptr;
        int bestScore = pickRiskyScore(color_stack[best]);
        for( int i=sptr+1; i<color_stack.length; i++ ) {
            if( bestScore == 1000000 ) return best; // Already max score
            int iScore = pickRiskyScore(color_stack[i]);
            if( iScore > bestScore )
                { best = i; bestScore = iScore; }
        }
        return best;
    }

    // Pick a live range to pull, that might not color.
    //
    // Picking a live range with a very large span, with defs and uses outside
    // loops means spilling a relative cheap live range and getting that
    // register over a large area.
    //
    // Picking a live range that is very close to coloring might allow it to
    // color despite being risky.
    private static int pickRiskyScore( LRG lrg ) {
        // Pick single-def clonables that are not right next to their single-use.
        // Failing to color these will clone them closer to their uses.
        if( !lrg._multiDef && lrg._machDef.isClone() ) {
            Node def = ((Node)lrg._machDef);
            Node use = ((Node)lrg._machUse);
            CFGNode cfg = def.cfg0();
            if( cfg != use.cfg0() || // Different blocks OR
              // Same block, but not close
              cfg._outputs.find(def) < cfg._outputs.find(use)+1 )
                return 1000000;
        }

        // Always pick callee-save registers as being very large area recovered
        // and very cheap to spill.
        if( lrg._machDef instanceof CalleeSaveNode )
            return 1000000-2-lrg._mask.firstReg();
        if( lrg._splitDef != null && lrg._splitDef. in(1) instanceof CalleeSaveNode &&
            lrg._splitUse != null && lrg._splitUse.out(0) instanceof ReturnNode )
            return 1000000-1;

        // TODO: cost/benefit model.  Perhaps counting loop-depth (freq) of def/use for cost
        // and "area" for benefit
        return 1000;
    }

    private static short biasColor( RegAlloc alloc, LRG lrg, short reg, RegMask mask ) {
        if( mask.size1() ) return reg;
        // Check chain of splits up the def-chain.  Take first allocated
        // register, and if it's available in the mask, take it.
        Node def = lrg._splitDef, use = lrg._splitUse;
        int tidx=0, cnt=0;

        while( def != null || use != null ) {
            if( cnt++ > 10 ) break;

            if( def != null ) {
                short bias = biasColor( alloc, def, mask );
                if( bias >= 0 ) return bias; // Good bias
                if( bias == -2 ) def = null; // Kill this side, no more searching
                else if( (tidx=biasable(def)) == 0 ) def = null;
            }

            if( use != null ) {
                short bias = biasColor( alloc, use, mask );
                if( bias >= 0 ) return bias; // Good bias
                if( bias == -2 ) use = null; // Kill this side, no more searching
                else if( biasable(use)==0 ) use = null;
            }

            if( def != null ) {
                short bias = biasColorNeighbors( alloc, def, mask );
                if( bias >= 0 ) return bias;
                // Advance def side
                def = def.in(tidx);
                if( alloc.lrg(def)==null ) def=null;
            }

            if( use != null ) {
                short bias = biasColorNeighbors( alloc, use, mask );
                if( bias >= 0 ) return bias;
                use = use.out(0);
                if( biasable(use)==0 ) use=null;
            }

        }
        return mask.firstReg();
    }

    private static int biasable(Node split) {
        if( split instanceof SplitNode ) return 1; // Yes biasable, advance is slot 1
        if( split instanceof PhiNode phi ) return phi.region() instanceof LoopNode ? 2 : 1;   // Yes biasable, advance is slot 1
        if( !(split instanceof MachNode mach) ) return 0; // Not biasable
        return mach.twoAddress();                         // Only biasable if 2-addr
    }

    // 3-way return:
    // - good bias reg, take it & exit
    // - this path is cutoff; do not search here anymore
    // - advance this side
    private static short biasColor( RegAlloc alloc, Node split, RegMask mask ) {
        short bias = alloc.lrg(split)._reg;
        if( bias != -1 ) {
            if( mask.test(bias) ) return bias; // Good bias
            else return -2;                    // Kill this side
        } else return -1;                      // Advance this side
    }

    // Check if we can match "split" color, or else trim "mask" to colors
    // "split" might get.
    private static short biasColorNeighbors( RegAlloc alloc, Node split, RegMask mask ) {
        LRG slrg = alloc.lrg(split);
        if( slrg._adj == null ) return -1; // No trimming

        // Can I limit my own choices to valid neighbor choices?
        for( LRG alrg : slrg._adj ) {
            short reg = alrg._reg;
            if( reg == -1 && alrg._mask.size1() )
                reg = alrg._mask.firstReg();
            if( reg != -1 ) {
                mask.clr(alrg._reg);
                if( mask.size1() )
                    return mask.firstReg();
            }
        }
        return -1;              // No obvious color choice, but mask got trimmed
    }

    private static void swap( LRG[] ary, int x, int y ) {
        LRG tmp = ary[x]; ary[x] = ary[y]; ary[y] = tmp;
    }
}
