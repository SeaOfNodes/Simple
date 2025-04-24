package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.MachConcreteNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;

public class AsrIARM extends MachConcreteNode implements MachNode {
    final int _imm;
    AsrIARM(Node asr, int imm) {
        super(asr);
        _inputs.pop();
        _imm = imm;
    }
    @Override public String op() { return "asri"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.WMASK; }
    @Override public void encoding( Encoding enc ) {
        short rd = enc.reg(this);
        short rn = enc.reg(in(1));
        assert _imm > 0;
        enc.add4(arm.imm_shift(arm.OPI_ASR,_imm, 0b111111, rn, rd));
    }
    // General form: "asri  rd = rs1 >> imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" >> #").p(_imm);
    }
}