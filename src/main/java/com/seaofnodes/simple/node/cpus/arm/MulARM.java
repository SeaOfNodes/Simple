package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// mulh signed multiply instruction(no-imm form)
public class MulARM extends MachConcreteNode implements MachNode {
    MulARM(Node mul) {super(mul);}
    @Override public String op() { return "mul"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.WMASK; }

    @Override public void encoding( Encoding enc ) { arm.madd(enc,this,arm.OP_MUL,31); }
    // General form: "rd = rs1 * rs2"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" * ").p(code.reg(in(2)));
    }
}
