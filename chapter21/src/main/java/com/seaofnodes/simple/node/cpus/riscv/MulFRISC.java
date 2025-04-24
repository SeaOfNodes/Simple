package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// fmul.d
public class MulFRISC extends MachConcreteNode implements MachNode {
    MulFRISC(Node mulf) {super(mulf);}
    @Override public String op() { return "mulf"; }
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return riscv.FMASK; }
    @Override public RegMask outregmap() { return riscv.FMASK; }
    @Override public void encoding( Encoding enc ) { riscv.rf_type(enc,this,riscv.RM.RNE,9); }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" * ").p(code.reg(in(2)));
    }
}
