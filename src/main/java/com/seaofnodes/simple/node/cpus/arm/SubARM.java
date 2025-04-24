package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class SubARM extends MachConcreteNode implements MachNode {
    SubARM(Node sub) { super(sub); }
    @Override public String op() { return "sub"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.WMASK; }

    @Override public void encoding( Encoding enc ) { arm.r_reg(enc,this,arm.OP_SUB); }

    // General form: "sub  # rd = rs1 - rs2"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" - ").p(code.reg(in(2)));
    }
}
