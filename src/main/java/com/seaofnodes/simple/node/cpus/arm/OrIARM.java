package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class OrIARM extends MachConcreteNode implements MachNode {
    final int _imm;
    OrIARM(Node or, int imm) {
        super(or);
        _inputs.pop();
        _imm = imm;
    }
    @Override public String op() { return "ori"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.WMASK; }
    @Override public void encoding( Encoding enc ) {
        arm.imm_inst_n(enc, this, in(1), arm.OPI_OR, _imm);
    }
    // General form: "ori  rd = rs1 | imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" | #").p(_imm);
    }
}
