package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.MachNode;

public class FunRISC extends FunNode implements MachNode {
    FunRISC(FunNode fun) {super(fun);}
    @Override public String op() { return "addi"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return null; }
    @Override public void postSelect(CodeGen code) { code.link(this);  }

    // Actual stack layout is up to each CPU.
    // X86, with too many args & spills:
    // | CALLER |
    // |  argN  | // slot 1, required by callER
    // +--------+
    // |  RPC   | // slot 0, required by callER
    // | callee | // slot 3, callEE
    // | callee | // slot 2, callEE
    // |  PAD16 |
    // +--------+
    @Override public void encoding( Encoding enc ) {
        // Size of local stack frame.
        // Can be negative if the stack args passed are never referenced
        int sz = Math.max(_maxSlot - _maxArgSlot,0);
        sz = ((sz+1) & -2)+1;   // Pad to 16
        _frameAdjust = (short)sz;
        if( sz == 0 ) return; // Skip if no frame adjust
        if( sz*-8 < -1L<<12 ) throw Utils.TODO();
        enc.add4(riscv.i_type(riscv.OP_IMM, riscv.SP, 0, riscv.SP, (_frameAdjust*-8) & 0xFFF));
    }

    @Override public void asm(CodeGen code, SB sb) {
        if( _frameAdjust != 0 )
            sb.p("rsp -= #").p(_frameAdjust*8);
    }
}
