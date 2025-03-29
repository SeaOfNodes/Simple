package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class DivFARM extends MachConcreteNode implements MachNode {
    DivFARM( Node divf) { super(divf); }
    @Override public String op() { return "divf"; }
    @Override public RegMask regmap(int i) { return arm.DMASK; }
    @Override public RegMask outregmap() {  return arm.DMASK; }

    // FDIV (scalar)
    @Override public void encoding( Encoding enc ) { arm.f_scalar(enc,this,arm.OPF_DIV); }
    // General form: "VDIF =  dst /= src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" / ").p(code.reg(in(2)));
    }
}
