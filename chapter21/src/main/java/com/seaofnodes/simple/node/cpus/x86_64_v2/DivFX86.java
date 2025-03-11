package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class DivFX86 extends MachConcreteNode implements MachNode {
    DivFX86( Node divf) { super(divf); }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return x86_64_v2.XMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() {  return x86_64_v2.XMASK; }
    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // F2 0F 5E /r DIVSD xmm1, xmm2/m64

        LRG div_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG div_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short reg1 = div_rg_1.get_reg();
        short reg2 = div_rg_2.get_reg();

        int beforeSize = bytes.size();
        bytes.write(x86_64_v2.rex(reg1 - x86_64_v2.XMM_OFFSET, reg2 - x86_64_v2.XMM_OFFSET, 0));

        // Fopcode
        bytes.write(0xF2);
        bytes.write(0x0F);
        bytes.write(0x5E);

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, reg1 - x86_64_v2.XMM_OFFSET, reg2 - x86_64_v2.XMM_OFFSET));
        return bytes.size() - beforeSize;
    }


    // General form: "divf  dst /= src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" / ").p(code.reg(in(2)));
    }

    @Override public String op() {
        // 8 bytes = 64 bits
        return "divf";
    }
}
