package com.seaofnodes.simple.node.cpus.arm;


import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// logical right shift immediate
public class LsrIARM extends MachConcreteNode implements MachNode {
    final int _imm;
    LsrIARM(Node lsr, int imm) {
        super(lsr);
        _inputs.pop();
        _imm = imm;
    }
    @Override public String op() { return "lsri"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.WMASK; }
    @Override public void encoding( Encoding enc ) {
        short rd = enc.reg(this);
        short rn = enc.reg(in(1));
        assert _imm > 0;
        enc.add4(arm.imm_shift(arm.OPI_LSR,_imm, 0b111111, rn,rd));
    }
    // General form: "lsri  rd = rs1 >>> imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" >>> #").p(_imm);
    }
}