package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// fcvt.d.w
// Converts a 32-bit signed integer, in integer register rs1 into a double-precision floating-point number in floating-point register rd.
public class I2F8RISC extends MachConcreteNode implements MachNode {
    I2F8RISC(Node i2f8) {super(i2f8);}
    @Override public String op() { return "cvtf"; }
    @Override public RegMask regmap(int i) { assert i==1; return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.FMASK; }
    @Override public void encoding( Encoding enc ) {
        short dst  = (short)(enc.reg(this )-riscv.F_OFFSET);
        short src1 =         enc.reg(in(1));
        int body = riscv.r_type(riscv.OP_FP,dst,riscv.RM.RNE.ordinal(),src1,0,0x69);
        enc.add4(body);
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p("(flt)").p(code.reg(in(1)));
    }
}
