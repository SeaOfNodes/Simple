package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class I2f8X86 extends MachConcreteNode implements MachNode {
    I2f8X86(Node i2f8 ) { super(i2f8); }
    @Override public String op() { return "cvtf"; }
    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.WMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }

    @Override public void encoding( Encoding enc ) {
        // F2 0F 2A /r CVTSI2SD xmm1, r32/m32
        short dst = (short)(enc.reg(this ) - x86_64_v2.XMM_OFFSET);
        short src =         enc.reg(in(1));

        // Fopcode
        enc.add1(0xF2);
        // rex prefix must come next (REX.W is not set)
        x86_64_v2.rexF(dst, src, 0, true, enc);

        enc.add1(0x0F).add1(0x2A);

        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, dst, src));
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p("(flt)").p(code.reg(in(1)));
    }
}
