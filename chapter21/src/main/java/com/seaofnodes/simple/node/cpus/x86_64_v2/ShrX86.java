package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class ShrX86 extends MachConcreteNode implements MachNode {
    ShrX86(Node shr) {
        super(shr);
    };
    // Register mask allowed on input i.
    // This is the normal calling convention

    // CL register must be used for input(2)
    @Override public RegMask regmap(int i) {
        if(i == 1) return x86_64_v2.WMASK;
        if(i == 2) return x86_64_v2.RCX_MASK;
        throw Utils.TODO();
    }

    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }

    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // REX.W + D3 /5	SHR r/m64, CL
        LRG shr_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));

        short reg1 = shr_rg_1.get_reg();
        int beforeSize = bytes.size();
        bytes.write(x86_64_v2.rex(0, reg1, 0));
        bytes.write(0xD3); // opcode

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0x05, reg1));

        return bytes.size() - beforeSize;
    }

    // General form
    // General form: "shr  dst >>> src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" >>> ").p(code.reg(in(1)));
    }

    @Override public String op() { return "shr"; }
}
