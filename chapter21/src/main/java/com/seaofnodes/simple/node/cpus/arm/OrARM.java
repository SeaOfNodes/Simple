package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// Not using the ORRS variant.
public class OrARM extends MachConcreteNode implements MachNode {
    OrARM(Node and) { super(and); }
    @Override public String op() { return "orr"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.RMASK; }
    @Override public void encoding( Encoding enc ) { arm.r_reg(enc,this,0b10101010); }
    // General form:  #rd = rs1 & rs2
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" | ").p(code.reg(in(2)));
    }

}
