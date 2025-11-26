package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class MulFARM extends MachConcreteNode implements MachNode{
    MulFARM(Node mulf) {super(mulf);}
    @Override public String op() { return "mulf"; }
    @Override public RegMask regmap(int i) { return arm.DMASK; }
    @Override public RegMask outregmap() { return arm.DMASK; }
    @Override public void encoding( Encoding enc ) { arm.f_scalar(enc,this,arm.OPF_MUL); }
    // Default on double precision for now(64 bits)
    // General form: "VMUL.f32  rd = src1 * src2
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" * ").p(code.reg(in(2)));
    }
}
