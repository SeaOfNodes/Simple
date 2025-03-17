package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class DivFX86 extends MachConcreteNode implements MachNode {
    DivFX86( Node divf) { super(divf); }
    @Override public String op() { return "divf"; }
    @Override public RegMask regmap(int i) { return x86_64_v2.XMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // F2 0F 5E /r DIVSD xmm1, xmm2/m64
        short dst = (short)(enc.reg(this ) - x86_64_v2.XMM_OFFSET);
        short src = (short)(enc.reg(in(2)) - x86_64_v2.XMM_OFFSET);

        // Fopcode
        enc.add1(0xF2);
        // rex prefix must come next (REX.W is not set)
        int rex =x86_64_v2.rex(dst, src, 0,false);
        if(rex != 0x40) enc.add1(rex);

        enc.add1(0x0F);
        enc.add1(0x5E);

        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, dst, src));
    }


    // General form: "divf  dst /= src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" /= ").p(code.reg(in(2)));
    }
}
