package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class CmpFX86 extends MachConcreteNode implements MachNode {
    CmpFX86( Node cmp ) { super(cmp); }
    @Override public String op() { return "cmpf"; }
    @Override public RegMask regmap(int i) { return x86_64_v2.XMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.FLAGS_MASK; }

    @Override public void encoding( Encoding enc ) {
        // UCOMISD
        short src1 = (short)(enc.reg(in(1)) - x86_64_v2.XMM_OFFSET);
        short src2 = (short)(enc.reg(in(2)) - x86_64_v2.XMM_OFFSET);
        // float opcode
        enc.add1(0x66);
        // rex prefix must come next (REX.W is not set)
        x86_64_v2.rexF(src1, src2, 0, false, enc);

        enc.add1(0x0F).add1(0x2E);
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, src1, src2));
    }

    // General form: "cmp src1,src2"
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="flags" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1))).p(", ").p(code.reg(in(2)));
    }
}
