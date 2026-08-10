package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.codegen.*;

// Keep a saved NZCV value in GPRs and stack slots through subsequent spills.
public class SplitGPRARM extends SplitARM {
    private static final RegMask MASK = new RegMask(arm.WR_BITS,-2L);
    SplitGPRARM(LRG lrg,String kind,byte round) { super(lrg,kind,round); }
    @Override public RegMask regmap(int i) { return MASK; }
    @Override public RegMask outregmap() { return MASK; }
}
