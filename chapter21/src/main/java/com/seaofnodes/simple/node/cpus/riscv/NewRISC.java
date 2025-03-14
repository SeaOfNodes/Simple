package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class NewRISC extends NewNode implements MachNode {
    // A pre-zeroed chunk of memory.
    NewRISC( NewNode nnn, AUIPC auipc ) {
        super(nnn);
        _inputs.add(auipc);     // Add high-half address
    }
    @Override public String op() { return "alloc"; }
    // Size and pointer result in standard calling convention; null for all the
    // memory aliases edges
    @Override public RegMask    regmap(int i) { return i == 1 ? riscv.A0_MASK : null; }
    @Override public RegMask outregmap(int i) { return i == 1 ? riscv.A0_MASK : null; }
    @Override public RegMask outregmap() { return null; }
    @Override public RegMask killmap() { return riscv.riscCallerSave(); }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        enc.relo(this);
        short rpc = enc.reg(this);
        // High half is where the TFP constant used to be, the last input
        short auipc = enc.reg(in(_inputs._len-1));
        int body = riscv.i_type(0x67, rpc, 0/*JALR.C*/, auipc, 0);
        enc.add4(body);
    }

    // General form: "alloc #bytes"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(size()));
    }
}
