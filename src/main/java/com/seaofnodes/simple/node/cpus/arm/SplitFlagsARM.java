package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.codegen.*;

// NZCV transfers require a GPR. Further splits can spill that GPR to the stack.
public class SplitFlagsARM extends SplitARM {
    private static final RegMask MASK = new RegMask(arm.WR_BITS,1L);
    SplitFlagsARM(String kind,byte round) { super(kind,round); }
    @Override public boolean isNonStackSplit() { return true; }
    @Override public RegMask regmap(int i) { return MASK; }
    @Override public RegMask outregmap() { return MASK; }

}
