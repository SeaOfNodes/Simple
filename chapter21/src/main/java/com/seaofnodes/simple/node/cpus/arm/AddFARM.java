package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class AddFARM extends MachConcreteNode implements MachNode {
    AddFARM(Node addf) { super(addf); }
    @Override public String op() { return "addf"; }
    @Override public RegMask regmap(int i) { return arm.DMASK; }
    @Override public RegMask outregmap() { return arm.DMASK; }

    //FADD (scalar)
    @Override public void encoding( Encoding enc ) { arm.f_scalar(enc,this,arm.OPF_OP_ADD);}

    // Default on double precision for now(64 bits)
    // General form: "addf  rd = src1 + src2
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" + ").p(code.reg(in(2)));
    }

}
