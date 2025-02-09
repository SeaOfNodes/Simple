package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;

abstract public class BuildLRG {
    // Compute live ranges in a single forwards pass.  Every def is a new live
    // range except live ranges are joined at Phi.  Also, gather the
    // intersection of register masks in each live range.  Example: "free
    // always zero" register forces register zero, and calling conventions
    // typically force registers.

    // Sets FAILED to the set of hard-conflicts (means no need for an IFG,
    // since it will not color, just split the conflicted ranges now).  Returns
    // true if no hard-conflicts, although we still might not color.
    public static boolean run(RegAlloc alloc) {
        for( Node bb : alloc._code._cfg )
            for( Node n : bb.outs() ) {
                if( n instanceof MachNode mach ) {
                    RegMask def_mask = mach.outregmap();
                    if( def_mask!=null && !alloc.hasLRG(n) ) {
                        LRG lrg = mach.twoAddress() == 0
                            ? alloc.newLRG(n) // Define a new LRG for N
                            : alloc.lrg2(n,mach.twoAddress()); // Use the matching 2-adr input
                        // Record mask and mach
                        if( !lrg.machDef(mach,def_mask.size1()).and(def_mask) )
                            alloc.failed(lrg); // Empty register mask, must split
                    }
                    // Now, look in the opposite direction. How are incoming
                    // LRGs affected by this node: For all uses, make live lrgs
                    for( int i=1; i<n.nIns(); i++ )
                        if( n.in(i)!=null ) {
                            LRG lrg = alloc.lrg(n.in(i));
                            if( lrg != null ) { // Anti-dep or other, no LRG
                                RegMask use_mask = mach.regmap(i);
                                if( !lrg.machUse(mach,(short)i,use_mask.size1()).and(use_mask) )
                                    alloc.failed(lrg); // Empty register mask, must split
                            }
                        }

                } else {
                    switch( n ) {
                    case PhiNode phi:
                        //    // All Phi inputs end up with the same LRG.
                        //    // Pass 1: find any pre-existing LRG, to avoid make-then-Union a LRG
                        //    // If any lrg is found, copy it forward, capturing the result if it changes
                        //    // and copy that forward. Moves toward correctness.
                        //    lrg = lrg(n);
                        //    for( int i=1; i<n.len(); i++ )
                        //        lrg = lrg==0 ? lrg(n.in(i)) : setLRG(n.in(i),lrg);
                        //    // If none, make one.
                        //    if( lrg==0 ) lrg = newLRG(-1L);
                        //    // Pass 2: everybody uses the same LRG because all existing have been found
                        //    if( lrg != n._lrg ) {
                        //        tmp = setLRG(n,lrg); assert tmp==lrg;
                        //        for( int i=1; i<n.len(); i++ )
                        //            { tmp = setLRG(n.in(i),lrg); assert tmp==lrg; }
                        //    }
                        //    break;
                        throw Utils.TODO();

                    case StopNode     stop: break;
                    case ConstantNode con : break;
                    default:    throw Utils.TODO();
                    }
                }

                // Multi-register defining
                //if( n instanceof MultiNode )
                //    for( Node use : n.outs() )
                //        if( use instanceof ProjNode proj && proj.outregmap()!=0 ) {
                //            assert proj._lrg==0;
                //            int twidx = proj.twoAddress();
                //            setLRG(proj, twidx == 0 ? newLRG(-1L) : lrg(n.in(twidx)));
                //            andMask(proj,proj.outregmap());
                //            MACHS.setX(proj._lrg,proj);
                //        }

            }

        return alloc.FAILED.isEmpty();
  }
}
