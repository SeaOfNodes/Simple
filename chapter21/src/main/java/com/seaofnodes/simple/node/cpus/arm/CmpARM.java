package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class CmpARM extends MachConcreteNode implements MachNode {
    CmpARM( Node cmp ) { super(cmp); }
    @Override public String op() { return "cmp"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.FLAGS_MASK; }

    // SUBS (shifted register)
    @Override public void encoding( Encoding enc ) { arm.r_reg_subs(enc,this,arm.OP_CMP); }

    // General form: "cmp  rs1, rs2"
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="flags" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1))).p(", ").p(code.reg(in(2)));
    }
}
