package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.SB;
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
        // Stack frame size: _maxSlot - max(arg), padded to 16.
        _maxArgSlot = riscv.maxSlot(enc._fun.sig());
        _frameAdjust = (short) (_maxSlot+1 - _maxArgSlot);
        if( _frameAdjust == 0 ) return; // Skip if no frame adjust
        enc.add4(riscv.i_type(riscv.OP_IMM, riscv.SP, 0, riscv.SP, (_frameAdjust*8) & 0xFFF));
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p("rsp += #").p(_frameAdjust*8);
    }
}
