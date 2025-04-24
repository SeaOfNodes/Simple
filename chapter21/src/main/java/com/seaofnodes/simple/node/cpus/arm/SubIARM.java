package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.MachConcreteNode;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;

public class SubIARM extends MachConcreteNode implements MachNode {
    final int _imm;
    SubIARM(Node sub, int imm) {
        super(sub);
        _inputs.pop();
        _imm = imm;
    }
    @Override public String op() { return _imm == -1 ? "dec" : "subi"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.WMASK; }

    @Override public void encoding( Encoding enc ) {
        arm.imm_inst(enc,this, in(1), arm.OPI_SUB,_imm);
    }

    // General form: "subi  rd = rs1 - imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" - #").p(_imm);
    }
}
