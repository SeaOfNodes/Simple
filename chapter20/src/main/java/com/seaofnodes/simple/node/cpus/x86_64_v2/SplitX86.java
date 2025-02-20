package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class SplitX86 extends MachConcreteNode implements MachNode {
    SplitX86( ) { super(new Node[2]); }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { return x86_64_v2.SPLIT_MASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.SPLIT_MASK; }

    @Override public boolean isSplit() { return true; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // REX.W + 8B /r	MOV r64, r/m64
        LRG split_rg = CodeGen.CODE._regAlloc.lrg(this);
        LRG reg_1_rg = CodeGen.CODE._regAlloc.lrg(in(1));

        short split_reg_1 = split_rg.get_reg();
        short split_reg_2 = reg_1_rg.get_reg();
        int beforeSize = bytes.size();

        bytes.write(x86_64_v2.rex(split_reg_1, split_reg_2, 0, 0));
        bytes.write(0x8B); // opcode

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, split_reg_1, split_reg_2));

        return bytes.size() - beforeSize;
    }

    // General form: "mov  dst = src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1)));
    }

    @Override public String op() { return "mov"; }
}
