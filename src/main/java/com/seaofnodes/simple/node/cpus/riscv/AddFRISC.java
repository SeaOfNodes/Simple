package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// fadd.d
public class AddFRISC extends MachConcreteNode implements MachNode {
    AddFRISC(Node addf) {super(addf);}
    @Override public String op() { return "addf"; }
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return riscv.FMASK; }
    @Override public RegMask outregmap() { return riscv.FMASK; }
    @Override public void encoding( Encoding enc ) { riscv.rf_type(enc,this,riscv.RM.RNE,1); }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" + ").p(code.reg(in(2)));
    }
}
