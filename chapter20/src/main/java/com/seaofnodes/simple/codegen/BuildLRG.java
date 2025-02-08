package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.CFGNode;

abstract public class BuildLRG {
  // Compute live ranges in a single forwards pass.  Every def is a new live
  // range except live ranges are joined at Phi.  Also, gather the
  // intersection of register masks in each live range.  Example: "free
  // always zero" register forces register zero, and calling conventions
  // typically force registers.

  // Sets FAILED to the set of hard-conflicts (means no need for an IFG, since it will
  // not color, just split the conflicted ranges now).
  // Returns true if no hard-conflicts, although we still might not color.
  public static boolean run(Ary<CFGNode> cfg) {
      throw Utils.TODO();
  }
}
