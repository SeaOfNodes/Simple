package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class AddIARM extends MachConcreteNode implements MachNode {
    final int _imm;
    AddIARM(Node add, int imm) {
        super(add);
        _inputs.pop();
        _imm = imm;
    }
    @Override public String op() {
        return _imm == 1 ? "inc" : (_imm == -1 ? "dec" : "addi");
    }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.WMASK; }
    //ADD (immediate)
    @Override public void encoding( Encoding enc ) {
        arm.imm_inst(enc,this, in(1), arm.OPI_ADD,_imm);
    }
    // General form: "addi  rd = rs1 + imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" + #").p(_imm);
    }
}
