package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.isa.Arm64;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;

public class NewARM extends NewNode implements MachNode, RIPRelSize {
    // A pre-zeroed chunk of memory.
    NewARM(NewNode nnn) { super(nnn); }
    @Override public void encoding( Encoding enc ) {
        // BL(branch with link)
        enc.external(this,"calloc").
            add4(Arm64.mov(Arm64.OP_MOVZ, 0, 1, 0)).   // movz x0,#1
            add4(Arm64.b(Arm64.OP_CALL, 0));
    }

    // Patch is for running "new" in a JIT.
    // Delta is from opcode start
    @Override public byte encSize(int delta) { return 4*2; }

    // Patch is for running "new" in a JIT.
    // Delta is from opcode start
    @Override public void patch(Encoding enc, int opStart, int opLen, int delta ) {
        // Negative patches are JIT emulator targets.
        if( opStart+delta < 0 ) {
            enc.patch4(opStart+4, Arm64.b_calloc(Arm64.OP_CALL, delta-4));
        } else {
            throw Utils.TODO();
        }
    }
    // General form: "alloc #bytes  PC"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p("\n");             // Multi-op trigger
        sb.p("ldi   ").p(code._mach.regs()[_arg2Reg]).p(" = #1\n");
        sb.p("call  #calloc");
    }
}
