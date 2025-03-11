package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class SubX86 extends MachConcreteNode implements MachNode {
    SubX86( Node sub ) { super(sub); }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return x86_64_v2.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // REX.W + 29 /r
        LRG sub_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG sub_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short reg1 = sub_rg_1.get_reg();
        short reg2 = sub_rg_2.get_reg();

        int beforeSize = bytes.size();

        bytes.write(x86_64_v2.rex(reg1, reg2, 0));
        bytes.write(0x2B); // opcode

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, reg1, reg2));

        return bytes.size() - beforeSize;
    }

    // General form: "sub  dst -= src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" - ").p(code.reg(in(2)));
    }

    @Override public String op() { return "sub"; }
}
