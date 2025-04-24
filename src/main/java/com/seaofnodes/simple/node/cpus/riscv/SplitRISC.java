package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.FunNode;
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
            int off = enc._fun.computeStackOffset(enc._code,dst);
            int op = srcX ? riscv.OP_STOREFP : riscv.OP_STORE;
            if( srcX ) src -= riscv.F_OFFSET;
            enc.add4(riscv.s_type(op, 0b011, riscv.RSP, src, off));
            return;
        }
        if( src >= riscv.MAX_REG ) {
            // Load from SP
            int off = enc._fun.computeStackOffset(enc._code,src);
            int op = dstX ? riscv.OP_LOADFP : riscv.OP_LOAD;
            if( dstX ) dst -= riscv.F_OFFSET;
            enc.add4(riscv.i_type(op, dst, 0b011, riscv.RSP, off));
            return;
        }
        // pick opcode based on regs
        if( !dstX && !srcX ) {
            // GPR->GPR
            enc.add4(riscv.r_type(riscv.OP,dst,0,src,riscv.ZERO,0));
        } else if( dstX && srcX ) {
            // FPR->FPR
            src -= riscv.F_OFFSET;
            dst -= riscv.F_OFFSET;
            enc.add4(riscv.r_type(riscv.OP_FP, dst, 0b000, src, src, 0b0010101)); // dst = FMIN(src,src)
        } else if(!srcX && dstX) {
            //GPR->FPR
            // fmv.d.x
            enc.add4(riscv.r_type(riscv.OP_FP, dst - riscv.F_OFFSET, 0, src, 0, 0b1111001));
        } else if(srcX && !dstX) {
            //FPR->GPR
            //fmv.x.d
            enc.add4(riscv.r_type(riscv.OP_FP, dst, 0, src - riscv.F_OFFSET, 0, 0b1110001));
        }
    }

    // General form: "mov  dst = src"
    @Override public void asm(CodeGen code, SB sb) {
        FunNode fun = code._encoding==null ? null : code._encoding._fun;
        sb.p(code.reg(this,fun)).p(" = ").p(code.reg(in(1),fun));
    }
}
