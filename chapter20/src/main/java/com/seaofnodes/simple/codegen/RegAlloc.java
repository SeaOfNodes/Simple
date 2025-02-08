package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.Node;

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
        return
            // Build Live Ranges
            BuildLRG.run(_code._cfg) &&  // if no hard register conflicts
            // Build Interference Graph
            IFG.build(round,_code._cfg) && // If no self conflicts or uncolorable
            // Color attempt
            IFG.color(round,_code);        // If colorable
    }

    // Split conflicted live ranges.
    void split() {
        throw Utils.TODO();
    }

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
