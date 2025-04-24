package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;
import java.util.BitSet;
import java.util.IdentityHashMap;

// Coalesce copies
abstract public class Coalesce {

    public static boolean coalesce( int round, RegAlloc alloc ) {

        // Walk all the splits, looking for coalesce chances
        boolean progress = false;
        for( CFGNode bb : alloc._code._cfg ) {
            outer:
            for( int j=0; j<bb.nOuts(); j++ ) {
                Node n = bb.out(j);
                if( !(n instanceof SplitNode) ) continue;
                LRG v1 = alloc.lrg(n);
                LRG v2 = alloc.lrg(n.in(1));
                if( v1 != v2 ) {
                    LRG ov1 = v1, ov2 = v2;
                    // Get the smaller neighbor count in v1
                    if( v1.nadj() > v2.nadj() )
                        { v1 = v2; v2 = alloc.lrg(n); }
                    int v2len = v2._adj==null ? 0 : v2._adj._len;
                    // See that they do not conflict (coalescing would make a self-conflict)
                    if( v1._adj!=null && v2._adj!=null && v1._mask.overlap(v2._mask) ) {
                        // Walk the smaller neighbors, and add to the larger.
                        // Check for direct conflicts along the way.
                        for( LRG v3 : v1._adj ) {
                            if( v3==v2 ) {
                                // Direct conflict, skip this LRG pair
                                v2._adj.setLen(v2len);
                                continue outer;
                            }
                            // Add missing LRGs from V1 to V2
                            if( v2._adj.find(v3)== -1 )
                                v2._adj.push(v3);
                        }
                    }
                    // Most constrained mask
                    RegMask mask = v1._mask==v2._mask ? v1._mask : v1._mask.copy().and(v2._mask);
                    // Check for capacity
                    if( v2.nadj() >= mask.size() ) {
                        // Fails capacity, will not be trivial colorable
                        if( v2._adj!=null ) v2._adj.setLen(v2len);
                        continue;
                    }

                    // Union lrgs.
                    progress = true;

                    // Since `n` is dying, remove from the LRGS BEFORE doing
                    // the union; this will let the union code keep the
                    // matching bits from the other side, preserving some
                    // info for biased coloring
                    if( ov1. _machDef==n ) ov1. _machDef = null;
                    if( ov1._splitDef==n ) ov1._splitDef = null;
                    if( ov2. _machUse==n ) ov2. _machUse = null;
                    if( ov2._splitUse==n ) ov2._splitUse = null;
                    // Keep larger adjacency list.
                    Ary<LRG> larger = v2._adj;
                    v2._adj = null;

                    // Union lrgs.  Swaps again, based on small lrg
                    LRG vWin = v1.union(v2);
                    vWin._adj = larger;
                    LRG vLose = v1.leader() ? v2 : v1;

                    // LRGs below v2len were always in v2, but maybe not in v1.
                    // LRGs above v2len are in v1 but were not in v2.

                    // Walk v2's neighbors, replacing vLose with vWin.
                    if( larger != null )
                        for( LRG vn : larger ) {
                            int idx = vn._adj.find(vLose);
                            if( idx != -1 )
                                vn._adj.del(idx);
                            if( vn._adj.find(vWin) == -1 )
                                vn._adj.add(vWin);
                        }
                } else {

                    // Since `n` is dying, remove from the LRGS
                    if( v1. _machDef==n ) v1. _machDef=null;
                    if( v1. _machUse==n ) v1. _machUse=null;
                    if( v1._splitDef==n ) v1._splitDef=null;
                    if( v1._splitUse==n ) v1._splitUse=null;
                }

                // Remove junk split
                n.removeSplit();
                j--;
            }
        }

        if( progress )
            alloc.unify();

        return true;
    }
}
