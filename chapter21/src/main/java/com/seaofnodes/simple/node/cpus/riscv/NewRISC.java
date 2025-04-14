package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.ASMPrinter;

public class NewRISC extends NewNode implements MachNode, RIPRelSize {
    // A pre-zeroed chunk of memory.
    NewRISC( NewNode nnn ) { super(nnn); }
    @Override public String op() { return "alloc"; }
    @Override public RegMask    regmap(int i) { return i==1 ? riscv.A1_MASK : null; }
    @Override public RegMask outregmap(int i) { return i==1 ? riscv.A0_MASK : null; }
    @Override public RegMask outregmap() { return null; }
    @Override public RegMask killmap() { return riscv.riscCallerSave(); }

    @Override public void encoding( Encoding enc ) {
        // Generic external encoding; 2 ops.
        enc.external(this,"calloc");
        // A1 is a caller-save, allowed to crush building external address
        // auipc
        enc.add4(riscv.i_type(riscv.OP_IMM  , riscv.A0 , 0, riscv.ZERO, 1));
        enc.add4(riscv.u_type(riscv.OP_AUIPC, riscv.A2 , 0));
        enc.add4(riscv.i_type(riscv.OP_JALR , riscv.RPC, 0, riscv.A2, 0));
    }

    // Patch is for running "new" in a JIT.
    // Delta is from opcode start
    @Override public byte encSize(int delta) { return 4*3; }

    // Patch is for running "new" in a JIT.
    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        // Odd-number patches are JIT emulator targets.
        if( (delta&3)!=0 ) {
            //enc.patch4(opStart+4, riscv.i_type(riscv.OP_IMM, riscv.ZERO , 0, riscv.ZERO, 0));
            //enc.patch4(opStart,riscv.j_type(riscv.OP_JAL, riscv.RPC, opStart+delta));

            int imm20 = delta>>12;
            if( ((delta>>11)&1)==1 ) imm20++; // Correct accidental sign extension
            int imm12 = delta&0xFFF;
            enc.patch4(opStart+4,riscv.u_type(riscv.OP_AUIPC, riscv.A2 , imm20));
            enc.patch4(opStart+8,riscv.i_type(riscv.OP_JALR , riscv.RPC, 0, riscv.A2, imm12));
        } else
            throw Utils.TODO();
    }

    // General form: "alloc #bytes  PC"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p("ldi   a1=#1\n");
        sb.p("auipc a2=#calloc\n");
        sb.p("call  a2+#calloc, a0");
    }
}
