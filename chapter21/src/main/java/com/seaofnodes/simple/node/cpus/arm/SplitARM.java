package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.SplitNode;
import com.seaofnodes.simple.node.cpus.riscv.riscv;

public class SplitARM extends SplitNode {
    SplitARM(String kind, byte round) { super(kind,round,new Node[2]);}
    @Override public RegMask regmap(int i) { return arm.SPLIT_MASK; }
    @Override public RegMask outregmap() { return arm.SPLIT_MASK; }

    @Override public void encoding( Encoding enc ) {
        short dst = enc.reg(this );
        short src = enc.reg(in(1));

        boolean dstX = dst >= arm.D_OFFSET;
        boolean srcX = src >= arm.D_OFFSET;

        if (dst >= arm.MAX_REG) {
            // store to sp
            if(src >= arm.MAX_REG) {
                throw Utils.TODO();
            }
            int off = enc._fun.computeStackSlot(dst - arm.MAX_REG)*8;
            enc.add4(arm.load_str_imm(arm.OP_STORE_IMM, off, arm.RSP, src));
            return;
        }

        if(src >= arm.MAX_REG) {
            // Load from SP
            int off = enc._fun.computeStackSlot(src - arm.MAX_REG) * 8;
            enc.add4(arm.load_str_imm(arm.OP_LOAD_IMM, off, arm.RSP, dst));
            return;
        }

        // pick opcode based on regs
        if(!dstX && !srcX) {
            // GPR->GPR
            enc.add4(arm.mov_reg(arm.OP_MOV, src, dst));
        } else if(dstX && srcX) {
            // FPR->FPR
            // fmov reg
            enc.add4(arm.f_mov_reg(arm.OP_FMOV_REG, src - arm.D_OFFSET,dst - arm.D_OFFSET));
        } else if(!srcX && dstX) {
            // GPR->FPR
            // FMOV(general) 64 bits to DOUBLE-PRECISION
            enc.add4(arm.f_mov_general(arm.OP_FMOV, 0b01, 0, 0b111, src, dst - arm.D_OFFSET));
        } else if( srcX && !dstX) {
            //FPF->GPR
            // FMOV(general) DOUBLE-PRECISION to 64 bits
            enc.add4(arm.f_mov_general(arm.OP_FMOV, 0b01, 0, 0b110, src - arm.D_OFFSET, dst));
        }
    }
}
