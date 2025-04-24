package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.isa.RiscV;
import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class F8ToIRISC extends MachConcreteNode implements MachNode {
    F8ToIRISC(Node f8toi) {super(f8toi);}
    @Override public String op() { return "cvti"; }
    @Override public RegMask regmap(int i) { assert i==1; return riscv.FMASK; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public void encoding( Encoding enc ) {
        RiscV.floatToInteger(enc,enc.reg(this),enc.reg(in(1))-riscv.F_OFFSET);
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p("(int)").p(code.reg(in(1)));
    }
}
