package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.SplitNode;

public class SplitRISC extends SplitNode {
    SplitRISC( String kind, byte round ) { super(kind,round, new Node[2]); }
    @Override public RegMask regmap(int i) { return riscv.SPLIT_MASK; }
    @Override public RegMask outregmap() { return riscv.SPLIT_MASK; }
    @Override public void encoding( Encoding enc ) {
        short dst = enc.reg(this );
        short src = enc.reg(in(1));

        // Flag for being either XMM or stack
        boolean dstX = dst >= riscv.F_OFFSET;
        boolean srcX = src >= riscv.F_OFFSET;

        if( dst >= riscv.MAX_REG ) {
            // Store to SP
            if( src >= riscv.MAX_REG ) {
                throw Utils.TODO(); // Very rare stack-stack move
            }

            int off = enc._fun.computeStackSlot(dst - riscv.MAX_REG)*8;
            // store 64 bit values
            enc.add4(riscv.s_type(39, 3, riscv.SP, dst, off));
        }
        if( src >= riscv.MAX_REG ) {
            // Load from SP
            int off = enc._fun.computeStackSlot(src - riscv.MAX_REG)*8;
            enc.add4(riscv.i_type(7, dst, 3,riscv.SP, off));
        }
        // pick opcode based on regs
        if( !dstX && !srcX ) {
            // GPR->GPR
            enc.add4(riscv.r_type(riscv.OP,dst,0,src,riscv.ZERO,0));
        } else if( dstX && srcX ) {
            // FPR->FPR
            // Can do: FPR->GPR->FPR
            enc.add4(riscv.r_type(0b1010011, dst, 0, src, 0, 0b1110001));
            enc.add4(riscv.r_type(0b1010011, dst, 0, src, 0, 0b0100000));
        } else if( dstX && !srcX ) {
            //GPR->FPR
            // fmv.d.x
            enc.add4(riscv.r_type(0b1010011, dst, 0, src, 0, 0b0100000));
        } else if( !dstX && srcX ) {
            //FPR->GPR
            //fmv.x.d
            enc.add4(riscv.r_type(0b1010011, dst, 0, src, 0, 0b1110001));
        }

    }
}
