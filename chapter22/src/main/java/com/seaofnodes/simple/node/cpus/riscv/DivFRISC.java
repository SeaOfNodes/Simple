package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// fdiv.d
public class DivFRISC extends MachConcreteNode implements MachNode{
    DivFRISC(Node divf) {super(divf);}
    @Override public String op() { return "divf"; }
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return riscv.FMASK; }
    @Override public RegMask outregmap() { return riscv.FMASK; }
    @Override public void encoding( Encoding enc ) { riscv.rf_type(enc,this,riscv.RM.RUP,13); }
    // Default on double precision for now(64 bits)
    // General form: "fdiv.d  rd = src1 / src2
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" / ").p(code.reg(in(2)));
    }
}
