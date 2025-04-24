package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class AddARM extends MachConcreteNode implements MachNode {
    AddARM( Node add) { super(add); }
    @Override public String op() { return "add"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.WMASK; }

    // ADD (shifted register)
    @Override public void encoding( Encoding enc ) { arm.r_reg(enc,this,arm.OP_ADD); }
    // General form: "rd = rs1 + rs2"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" + ").p(code.reg(in(2)));
    }

}
