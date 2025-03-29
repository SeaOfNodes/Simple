package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.ASMPrinter;

public class NewRISC extends NewNode implements MachNode {
    // A pre-zeroed chunk of memory.
    NewRISC( NewNode nnn ) { super(nnn); }
    @Override public String op() { return "alloc"; }
    @Override public RegMask regmap(int i) {
        return i==1 ? riscv.A0_MASK : null; // Size input or mem aliases
    }
    @Override public RegMask outregmap(int i) { return i == 1 ? riscv.A0_MASK : null; }
    @Override public RegMask outregmap() { return null; }
    @Override public RegMask killmap() { return riscv.riscCallerSave(); }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // Generic external encoding; 2 ops.
        enc.external(this,"calloc");
        // A1 is a caller-save, allowed to crush building external address
        // auipc
        enc.add4(riscv.u_type(riscv.OP_AUIPC, riscv.A1, 0));
        enc.add4(riscv.i_type(riscv.OP_CALL, riscv.RPC, 0, riscv.A1, 0));
    }

    // General form: "alloc #bytes  PC"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p("auipc a1=#calloc\n");
        sb.p("call  a1+#calloc, a0");
    }
}
