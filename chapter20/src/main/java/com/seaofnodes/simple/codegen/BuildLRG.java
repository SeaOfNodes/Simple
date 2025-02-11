package com.seaofnodes.simple.codegen;

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
    public static boolean run(int round, RegAlloc alloc) {
        for( Node bb : alloc._code._cfg )
            for( Node n : bb.outs() ) {
                if( n instanceof MachNode mach ) {
                    RegMask def_mask = mach.outregmap();
                    if( def_mask!=null ) {
                        LRG lrg = mach.twoAddress() == 0
                            ? alloc.newLRG(n) // Define a new LRG for N
                            : alloc.lrg2(n,mach.twoAddress()); // Use the matching 2-adr input
                        // Record mask and mach
                        // def_mask.size()->1 : single register mask
                        if( !lrg.machDef(mach,def_mask.size1()).and(def_mask) )
                            alloc.fail(lrg); // Empty register mask, must split
                    }

                    // Now, look in the opposite direction. How are incoming
                    // LRGs affected by this node: For all uses, make live lrgs
                    for( int i=1; i<n.nIns(); i++ )
                        if( n.in(i)!=null ) {
                            LRG lrg2 = alloc.lrg(n.in(i));
                            if( lrg2 != null ) { // Anti-dep or other, no LRG
                                RegMask use_mask = mach.regmap(i);
                                if( !lrg2.machUse(mach,(short)i,use_mask.size1()).and(use_mask) )
                                    alloc.fail(lrg2); // Empty register mask, must split
                            }
                        }

                } else if( n instanceof PhiNode phi ) {
                    // All Phi inputs end up with the same LRG.
                    // Pass 1: find any pre-existing LRG, to avoid make-then-Union a LRG
                    LRG lrg = alloc.lrg(phi);
                    if( lrg == null )
                        for( int i=1; i<phi.nIns(); i++ )
                            if( (lrg = alloc.lrg(phi.in(i))) != null )
                                break;
                    // If none, make one.
                    if( lrg==null ) lrg = alloc.newLRG(n);
                    // Pass 2: everybody uses the same LRG
                    alloc.union(lrg,phi);
                    for( int i=1; i<n.nIns(); i++ )
                        alloc.union(lrg,n.in(i));
                    if( lrg._mask.isEmpty() )
                        alloc.fail(lrg);
                }
            }

        // Collect live ranges
        alloc.unify();

        return alloc.success();
  }
}
