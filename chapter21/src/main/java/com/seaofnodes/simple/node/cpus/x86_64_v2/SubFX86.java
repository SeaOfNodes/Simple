package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class SubFX86 extends MachConcreteNode implements MachNode {
    SubFX86( Node subf) {  super(subf); }
    @Override public String op() { return "subf"; }
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return x86_64_v2.XMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }
    @Override public int twoAddress() { return 1; }

    @Override public void encoding( Encoding enc ) {
        // F2 0F 5C /r SUBSD xmm1, xmm2/m64
        short dst = (short)(enc.reg(this ) - x86_64_v2.XMM_OFFSET);
        short src = (short)(enc.reg(in(2)) - x86_64_v2.XMM_OFFSET);
        // Fopcode
        enc.add1(0xF2);
        // rex prefix must come next (REX.W is not set)
        x86_64_v2.rexF(dst, src, 0, false, enc);

        enc.add1(0x0F).add1(0x5C);
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, dst, src));
    }

    // General form: "subf  dst -= src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" -= ").p(code.reg(in(2)));
    }
}
