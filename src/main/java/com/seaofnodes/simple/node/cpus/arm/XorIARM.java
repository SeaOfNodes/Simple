package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.node.MachConcreteNode;
import com.seaofnodes.simple.node.MachNode;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class XorIARM extends MachConcreteNode implements MachNode {
    final int _imm;
    XorIARM(Node xor, int imm) {
        super(xor);
        _inputs.pop();
        _imm = imm;
    }

    @Override public String op() { return "xori"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.WMASK; }

    // General form: "xori  rd = rs1 ^ imm"
    @Override public void encoding( Encoding enc ) { arm.imm_inst_n(enc,this, in(1), arm.OPI_XOR,_imm); }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" ^ #").p(_imm);
    }
}
