package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.isa.Arm64;
import com.seaofnodes.simple.codegen.*;

// NZCV transfers require a GPR. Further splits can spill that GPR to the stack.
public class SplitFlagsARM extends SplitARM {
    private static final RegMask MASK = new RegMask(arm.WR_BITS,1L);
    SplitFlagsARM(String kind,byte round) { super(kind,round); }
    @Override public boolean isNonStackSplit() { return true; }
    @Override public RegMask regmap(int i) { return MASK; }
    @Override public RegMask outregmap() { return MASK; }

    @Override public void encoding(Encoding enc) {
        short dst=enc.reg(this), src=enc.reg(in(1));
        if( dst==src ) return;
        if( src==arm.FLAGS ) enc.add4(Arm64.flags(dst,false));
        else if( dst==arm.FLAGS ) enc.add4(Arm64.flags(src,true));
        else super.encoding(enc);
    }
}
