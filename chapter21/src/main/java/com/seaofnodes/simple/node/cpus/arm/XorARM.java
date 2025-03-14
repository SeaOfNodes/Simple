package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class XorARM extends MachConcreteNode implements MachNode{
    XorARM(Node xor) {super(xor);}
    @Override public String op() { return "xor"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.RMASK; }
    @Override public void encoding( Encoding enc ) { arm.r_reg(enc,this,0b11001010); }
    // General form: "rd = x1 ^ x2"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" ^ ").p(code.reg(in(2)));
    }

}
