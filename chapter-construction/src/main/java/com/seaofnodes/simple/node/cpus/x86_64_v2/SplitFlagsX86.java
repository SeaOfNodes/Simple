package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;

// Flag snapshots can use GPRs or stack slots, but never XMM registers.
public class SplitFlagsX86 extends SplitX86 {
    private static final RegMask MASK = new RegMask(x86_64_v2.WR_BITS | (1L<<x86_64_v2.FLAGS) | x86_64_v2.SPILLS,-1L);
    SplitFlagsX86(String kind,byte round) { super(kind,round); }
    @Override public RegMask regmap(int i) { return MASK; }
    @Override public RegMask outregmap() { return MASK; }

    @Override public void encoding(Encoding enc) {
        short dst=enc.reg(this), src=enc.reg(in(1));
        if( dst==src ) return;
        if( dst==x86_64_v2.FLAGS || src==x86_64_v2.FLAGS ) {
            boolean toFlags = dst==x86_64_v2.FLAGS;
            int reg = toFlags ? src : dst;
            if( reg>=x86_64_v2.MAX_REG )
                X86.flagsStack(enc,enc._fun.computeStackOffset(enc._code,reg),toFlags);
            else
                X86.flags(enc,reg,toFlags);
        } else super.encoding(enc);
    }
}
