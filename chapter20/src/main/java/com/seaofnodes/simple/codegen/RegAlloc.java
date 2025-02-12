package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.*;

import java.util.BitSet;
import java.util.IdentityHashMap;

/**
  * "Briggs/Chaitin/Click".
  * Graph coloring.
  *
  * Every def and every use has a bit-set of allowed registers.
  * Fully general to bizarre chips.
  * Multiple outputs fully supported, e.g. add-with-carry or combined div/rem.
  *
  * Intel 2-address accumulator-style ops fully supported.
  * Op-to-stack-spill supported.
  * All addressing modes supported.
  * Register pairs supported with some grief.
  *
  * Splitting instead of spilling (simpler implementation).
  * Stack slots are "just another register" (tiny stack frames, simpler implementation).
  * Stack/unstack during coloring with a marvelous trick (simpler implementation).
  *
  * Both bitset and adjacency list formats for the interference graph; one of
  * the few times its faster to change data structures mid-flight rather than
  * just wrap one of the two.
  *
  * Liveness computation and interference graph built in the same one pass (one
  * fewer passes per round of coloring).
  *
  * Single-register def or use live ranges deny neighbors their required
  * register and thus do not interfere, vs interfering and denying the color
  * and coloring time.  Basically all function calls do this, but many oddball
  * registers also, e.g. older div/mod/mul ops. (5x smaller IFG, the only
  * O(n^2) part of this operation).
  */

public class RegAlloc {
    // Main Coloring Algorithm:
    // Repeat until colored:
    //   Build Live Ranges (LRGs)
    //   - Intersect allowed registers
    //   If hard conflicts (LRGs with no allowed registers)
    //   - Pre-split conflicted LRGs, and repeat
    //   Build Interference Graph (IFG)
    //   - Self conflicts split now
    //   Color (remove trivial first then any until empty; reverse assign colors
    //   - If color fails:
    //   - - Split uncolorable LRGs

    // Top-level program graph structure
    final CodeGen _code;

    // -----------------------
    // Live ranges with self-conflicts or no allowed registers
    private final IdentityHashMap<LRG,String> _failed = new IdentityHashMap<>();
    void fail( LRG lrg ) {
        assert !lrg.unified();
        _failed.put(lrg,"");
    }
    boolean success() { return _failed.isEmpty(); }


    // -----------------------
    // Map from Nodes to Live Ranges
    private final IdentityHashMap<Node,LRG> _lrgs = new IdentityHashMap<>();
    short _lrg_num;

    // Has a LRG defined
    boolean hasLRG( Node n ) { return _lrgs.containsKey(n);  }

    // Define a new LRG, and assign n
    LRG newLRG( Node n ) {
        LRG lrg = lrg(n);
        if( lrg!=null ) return lrg;
        lrg = new LRG(_lrg_num++);
        LRG old = _lrgs.put(n,lrg); assert old==null;
        return lrg;
    }

    // LRG for n
    LRG lrg( Node n ) {
        LRG lrg = _lrgs.get(n);
        if( lrg==null ) return null;
        LRG lrg2 = lrg.find();
        if( lrg != lrg2 )
            _lrgs.put(n,lrg2);
        return lrg2;
    }

    // Find LRG for n.in(idx), and also map n to it
    LRG lrg2( Node n, int idx ) {
        LRG lrg = lrg(n.in(idx));
        return union(lrg,n);
    }

    // Union any lrg for n with lrg and map to the union
    LRG union( LRG lrg, Node n ) {
        LRG lrgn = _lrgs.get(n);
        LRG lrg3 = lrg.union(lrgn);
        _lrgs.put(n,lrg3);
        return lrg3;
    }

    // Force all unified to roll up; collect live ranges
    LRG[] _LRGS;
    void unify() {
        Ary<LRG> lrgs = new Ary<>(LRG.class);
        for( Node n : _lrgs.keySet() ) {
            LRG lrg = lrg(n);
            lrgs.setX(lrg._lrg,lrg);
        }
        _LRGS = lrgs.asAry();
    }



    // Printable register number for node n
    String reg( Node n ) {
        LRG lrg = lrg(n);
        if( lrg==null ) return null;
        if( lrg._reg == -1 ) return "V"+lrg._lrg;
        return _code._mach.reg(lrg._reg);
    }

    // -----------------------
    RegAlloc( CodeGen code ) { _code = code; }

    public void regAlloc() {
        // Top driver: repeated rounds of coloring and splitting.
        int round=0;
        while( !graphColor(round) ) {
            split();
            assert round < 7; // Really expect to be done soon
            round++;
        }
        postColor();                       // Remove no-op spills
    }

    private boolean graphColor(int round) {
        _failed.clear();
        _lrgs.clear();
        _lrg_num = 1;

        return
            // Build Live Ranges
            BuildLRG.run(round,this) &&    // if no hard register conflicts
            // Build Interference Graph
            IFG.build(round,this) && // If no self conflicts or uncolorable
            // Color attempt
            IFG.color(round,this);   // If colorable
    }

    // -----------------------
    // Split conflicted live ranges.
    void split() {

        // In C2, all splits are handling in one pass over the program.  Here,
        // in the name of clarity, we'll handle each failing live range
        // independently... which generally requires a full pass over the
        // program for each failing live range.  i.e., might be a lot of
        // passes.
        for( LRG lrg : _failed.keySet() )
            split(lrg);
    }

    // Split this live range
    boolean split( LRG lrg ) {
        assert !lrg.unified();  // Already rolled up

        // Register mask when empty; split around defs and uses with limited
        // register masks.
        if( lrg._mask.isEmpty() &&
             lrg._1regDefCnt <= 1 &&
             lrg._1regUseCnt <= 1 &&
            (lrg._1regDefCnt + lrg._1regUseCnt) > 0 )
            return splitEmptyMask(lrg);

        if( lrg._selfConflicts != null )
            return splitSelfConflict(lrg);

        // Generic split-by-loop depth.
        return splitByLoop(lrg);
    }

    // Split live range with an empty mask.  Specifically forces splits at
    // single-register defs or uses and not elsewhere.
    boolean splitEmptyMask( LRG lrg ) {
        // Live range has a single-def single-register, and/or a single-use
        // single-register.  Split after the def and before the use.  Does not
        // require a full pass.

        // Split just after def
        if( lrg._1regDefCnt==1 )
            _code._mach.split().insertAfter((Node)lrg._machDef);
        // Split just before use
        if( lrg._1regUseCnt==1 )
            _code._mach.split().insertBefore((Node)lrg._machUse, lrg._uidx);
        return true;
    }

    // Self conflicts require Phis (or two-address).
    // Insert a split after every def.
    boolean splitSelfConflict( LRG lrg ) {
        for( Node def : lrg._selfConflicts.keySet() ) {
            _code._mach.split().insertAfter(def);
            if( def instanceof PhiNode phi )
                _code._mach.split().insertBefore(phi,1);
        }
        return true;
    }


    // Generic: split around the outermost loop with non-split def/uses.  This
    // covers both self-conflicts (once we split deep enough) and register
    // pressure issues.
    boolean splitByLoop( LRG lrg ) {
        findAllLRG(lrg);

        // Find min loop depth for all non-split defs and uses.
        long ld = (-1L<<32) | 9999;
        for( Node n : _ns ) {
            if( lrg(n)==lrg ) // This is a LRG def
                ld = ldepth(ld,n,n.cfg0());
            // PhiNodes check all CFG inputs
            if( n instanceof PhiNode phi ) {
                for( int i=1; i<n.nIns(); i++ )
                    ld = ldepth(ld, phi.in(i), phi.region().cfg(i));
            } else {
                // Others check uses
                for( int i=1; i<n.nIns(); i++ )
                    if( lrg(n.in(i))==lrg ) // This is a LRG use
                        ld = ldepth(ld,n.in(i),n.in(i).cfg0());
            }
        }
        int min = (int)ld;
        int max = (int)(ld>>32);


        // If the minLoopDepth is less than the maxLoopDepth: for-all defs and
        // uses, if at minLoopDepth or lower, split after def and before use.
        for( Node n : _ns ) {
            if( n instanceof MachNode mach && mach.isSplit() ) continue; // Ignoring splits; since spilling need to split in a deeper loop
            if( lrg(n)==lrg && // This is a LRG def
                (min==max || n.cfg0().loopDepth() <= min) )
                // Split after def in min loop nest
                _code._mach.split().insertAfter(n);

            // PhiNodes check all CFG inputs
            if( n instanceof PhiNode phi ) {
                for( int i=1; i<n.nIns(); i++ )
                    // No split in front of a split
                    if( !(n.in(i) instanceof MachNode mach && mach.isSplit()) &&
                        // splitting in inner loop or at loop border
                        (min==max || phi.region().cfg(i).loopDepth() <= min) &&
                        // and not around the backedge of a loop (bad place to force a split, hard to remove)
                        !(phi.region() instanceof LoopNode && i==2) )
                        // Split before phi-use in prior block
                        _code._mach.split().insertBefore(phi, i);

            } else {
                // Others check uses
                for( int i=1; i<n.nIns(); i++ ) {
                    if( lrg(n.in(i))==lrg && // This is a LRG use
                        !(n.in(i) instanceof MachNode mach && mach.isSplit()) &&
                        (min==max || n.cfg0().loopDepth() <= min) )
                        // Split before in this block
                        _code._mach.split().insertBefore(n, i);
                }
            }
        }
        return true;
    }

    private static long ldepth( long ld, Node n, CFGNode cfg ) {
        // Do not count splits
        if( n instanceof MachNode mach && mach.isSplit() ) return ld;
        // Collect min/max loop depth
        int min = (int)ld;
        int max = (int)(ld>>32);
        int d = cfg.loopDepth();
        min = Math.min(min,d);
        max = Math.max(max,d);
        return ((long)max<<32) | min;
    }

    // Find all members of a live range, both defs and uses
    private final Ary<Node> _ns = new Ary<>(Node.class);
    void findAllLRG( LRG lrg ) {
        _ns.clear();
        int wd = 0;
        _ns.push((Node)lrg._machDef);
        _ns.push((Node)lrg._machUse);
        while( wd < _ns._len ) {
            Node n = _ns.at(wd++);
            if( lrg(n)!=lrg ) continue;
            for( Node def : n._inputs )
                if( lrg(def)==lrg && _ns.find(def)== -1 )
                    _ns.push(def);
            for( Node use : n._outputs )
                if( _ns.find(use)== -1 )
                    _ns.push(use);
        }
    }



    // -----------------------
    // POST PASS: Remove empty spills that biased-coloring made
    private void postColor() {
        for( Node bb : _code._cfg ) { // For all ops
            for( int j=0; j<bb.nOuts(); j++ ) {
                Node n = bb.out(j);
                if( n instanceof MachNode mach && mach.isSplit() ) {
                    int defreg = lrg(n      )._reg;
                    int usereg = lrg(n.in(1))._reg;
                    if( defreg == usereg ) {
                        n.remove();
                        j--;
                    }
                }
                //if( live && n instanceof Mach m )  m.post_allocate();
            }
        }
    }
}
