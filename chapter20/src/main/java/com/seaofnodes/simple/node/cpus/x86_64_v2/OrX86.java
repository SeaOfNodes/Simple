package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import java.io.ByteArrayOutputStream;

public class OrX86 extends MachConcreteNode implements MachNode{
    OrX86(Node or) {
        super(or);
    }

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.WMASK; }

    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }

    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // REX.W + 0B /r	OR r64, r/m64
        LRG or_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG or_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short reg1 = or_rg_1.get_reg();
        short reg2 = or_rg_2.get_reg();

        int beforeSize = bytes.size();

        bytes.write(x86_64_v2.rex(reg1, reg2, 0));
        bytes.write(0x0B); // opcode

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, reg1, reg2));

        return bytes.size() - beforeSize;
    }

    // General form
    // General form: "or  dst | src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1)));
    }

    @Override public String op() { return "or"; }
}
