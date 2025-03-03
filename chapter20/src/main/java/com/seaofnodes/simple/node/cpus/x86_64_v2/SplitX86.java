package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.SplitNode;
import java.io.ByteArrayOutputStream;

public class SplitX86 extends SplitNode {
    SplitX86( String kind, byte round ) { super(kind,round, new Node[2]); }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { return x86_64_v2.SPLIT_MASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.SPLIT_MASK; }

    // Need to handle 8 cases: . reg->reg, reg->xmm, reg->flags, xmm->reg, xmm->xmm, xmm->flags, flags->reg, flags->xmm,
    // flags->flags.
    // Currently not handling flags
    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // REX.W + 8B /r	MOV r64, r/m64
        LRG split_rg = CodeGen.CODE._regAlloc.lrg(this);
        LRG reg_1_rg = CodeGen.CODE._regAlloc.lrg(in(1));

        short split_reg_1 = split_rg.get_reg();

        short split_reg_2 = reg_1_rg.get_reg();

        int beforeSize = bytes.size();

        if(split_reg_1 == x86_64_v2.FLAGS) {
            // mov flags, reg
            // pushf; pop reg
            bytes.write(0x9C);
            // 58+ rd	POP r64
            bytes.write(0x58 + split_reg_2);
            return bytes.size() - beforeSize; // early return
        } else if(split_reg_2 == x86_64_v2.FLAGS) {
            // mov reg, flags
            // push rcx
            // popf (Pop the top of the stack into the FLAGS register)
            // 50+rd	PUSH r64
            bytes.write(0x50 + split_reg_1);
            // popf
            bytes.write(0x9D);
            return bytes.size() - beforeSize; // early return
        }

        boolean reg_1_xmm = split_reg_1 >= 16 ? true  : false;
        boolean reg_2_xmm = split_reg_2 >= 16 ? true  : false;

        if((reg_1_xmm || reg_2_xmm) && !(reg_1_xmm && reg_2_xmm)) {
            bytes.write(0x66);
        }

        bytes.write(x86_64_v2.rex(reg_1_xmm ? split_reg_1 - x86_64_v2.XMM_OFFSET : split_reg_1,
                reg_2_xmm? split_reg_2 - x86_64_v2.XMM_OFFSET : split_reg_2, 0));

        // pick opcode based on regs
        if(split_reg_1 < 16 && split_reg_2 < 16) {
            // reg->reg (MOV r64, r/m64)
            bytes.write(0x8B);
        } else if(split_reg_1 >= 16 && split_reg_2 >= 16) {
            // xmm->xmm (NP 0F 28 /r MOVAPS xmm1, xmm2/m128)
            bytes.write(0x0F);
            bytes.write(0x28);
        } else if(split_reg_1 >= 16 && split_reg_2 < 16) {
            // xmm->reg (66 REX.W 0F 6E /r MOVQ xmm, r/m64)
            bytes.write(0x0F);
            bytes.write(0x6E);
        } else if(split_reg_1 < 16 && split_reg_2 >= 16) {
            // reg->xmm(66 REX.W 0F 7E /r MOVQ r/m64, xmm)
            bytes.write(0x0F);
            bytes.write(0x7E);
        }


        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, split_reg_1, split_reg_2));

        return bytes.size() - beforeSize;
    }

    // General form: "mov  dst = src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1)));
    }

    @Override public String op() { return "mov"; }
}
