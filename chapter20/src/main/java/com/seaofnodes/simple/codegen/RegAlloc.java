package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.Node;
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

    // Live ranges with self-conflicts or no allowed registers
    final Ary<LRG> FAILED = new Ary<>(LRG.class);

    // -----------------------
    // Map from Nodes to Live Ranges
    private final IdentityHashMap<Node,LRG> _lrgs = new IdentityHashMap<>();
    short _lrg_num;

    // Has a LRG defined
    boolean hasLRG( Node n ) { return _lrgs.containsKey(n);  }

    // Define a new LRG, and assign n
    LRG newLRG( Node n ) {
        LRG lrg = new LRG(_lrg_num++);
        LRG old = _lrgs.put(n,lrg); assert old==null;
        return lrg;
    }

    // LRG for n
    LRG lrg( Node n ) { return _lrgs.get(n); }

    // Find LRG for n.in(idx), and also map n to it
    LRG lrg2( Node n, int idx ) {
        LRG lrg = _lrgs.get(n.in(idx));
        _lrgs.put(n,lrg);       // Another node to the same live range
        return lrg;
    }


    // Printable register number for node n
    String reg( Node n ) {
        LRG lrg = lrg(n);
        if( lrg==null ) return null;
        return "V"+lrg._lrg;
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
        FAILED.clear();
        _lrgs.clear();
        _lrg_num = 1;

        return
            // Build Live Ranges
            BuildLRG.run(this) &&          // if no hard register conflicts
            // Build Interference Graph
            IFG.build(round,_code._cfg) && // If no self conflicts or uncolorable
            // Color attempt
            IFG.color(round,_code);        // If colorable
    }

    // -----------------------
    // Split conflicted live ranges.
    void split() {

        // In C2, all splits are handling in one pass over the program.  Here,
        // in the name of clarity, we'll handle each failing live range
        // independently... which generally requires a full pass over the
        // program for each failing live range.  i.e., might be a lot of
        // passes.
        for( LRG lrg : FAILED )
            split(lrg);
    }

    // Split this live range
    boolean split( LRG lrg ) {

        if( lrg._mask.isEmpty() )
            return splitEmptyMask(lrg);

        throw Utils.TODO();
    }

    // Split live range with an empty mask
    boolean splitEmptyMask( LRG lrg ) {
        // Live range has a single-def single-register, and/or a single-use
        // single-register.  Split after the def and before the use.  Does not
        // require a full pass.
        if( lrg._1regDefCnt<=1 && lrg._1regUseCnt<=1 ) {
            // Split just after def
            if( lrg._1regDefCnt==1 )
                _code._mach.split().insertAfter((Node)lrg._machDef);
            // Split just before use
            if( lrg._1regUseCnt==1 )
                _code._mach.split().insertBefore((Node)lrg._machUse, lrg._uidx);
            return true;
        }
        // Needs a full pass to find all defs and all uses
        throw Utils.TODO();
    }

    // -----------------------
    // POST PASS: Remove empty spills that biased-coloring made
    private void postColor() {
        for( Node bb : _code._cfg ) { // For all ops
            for( int j=0; j<bb.nOuts(); j++ ) {
                Node n = bb.out(j);
                boolean live = true;
                //if( n.isSpill() ) {
                //    int defreg = IFG.REGS.at(BuildLRG.lrg(n));
                //    int usereg = IFG.REGS.at(BuildLRG.lrg(n.in(1)));
                //    if( defreg == usereg ) {
                //        n.delCFG(); // Remove spill from block
                //        Node spilt = n.in(1);
                //        n.insert(spilt); // Insert the spill's input in front of spill's uses
                //        spilt.keep();
                //        n.kill("blank spill");
                //        spilt.unkeep();
                //        live=false;
                //        j--;
                //    }
                //}
                //if( live && n instanceof Mach m )  m.post_allocate();
                throw Utils.TODO();
            }
        }
    }

    // Collect live ranges which did not color, or a self-conflict or
    // register mask is empty.
    void failed(LRG lrg) {
        if( FAILED.find(lrg)== -1 )
            FAILED.push(lrg);
    }
}
