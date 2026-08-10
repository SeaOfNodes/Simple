package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.isa.Arm64;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

public class ModARM extends MachConcreteNode implements MachNode {
    private static final RegMask REGS=new RegMask(arm.WR_BITS & ~(1L<<arm.X16));
    ModARM(Node mod) { super(mod); }
    @Override public String op() { return "mod"; }
    @Override public RegMask regmap(int i) { return REGS; }
    @Override public RegMask outregmap() { return REGS; }
    @Override public RegMask killmap() { return new RegMask(1L<<arm.X16,1L); }
    @Override public void encoding(Encoding enc) {
        int lhs=enc.reg(in(1)), rhs=enc.reg(in(2)), dst=enc.reg(this);
        enc.add4(Arm64.imm_inst(Arm64.OPI_CMP,0,rhs,31)); // CMP rhs,0
        enc.add4(Arm64.b_cond(Arm64.OP_BRANCH,20,Arm64.COND.EQ));
        enc.add4(Arm64.madd(Arm64.OP_DIV,rhs,3,lhs,arm.X16)); // SDIV x16,lhs,rhs
        enc.add4(Arm64.madd(Arm64.OP_MUL,rhs,31,arm.X16,arm.X16)); // MUL x16,x16,rhs
        enc.add4(Arm64.r_reg(Arm64.OP_SUB,0,arm.X16,0,lhs,dst)); // SUB dst,lhs,x16
        enc.add4(Arm64.b(Arm64.OP_UJMP,8));
        enc.add4(Arm64.mov(Arm64.OP_MOVZ,0,0,dst));
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" % ").p(code.reg(in(2)));
    }
    @Override public String comment() { return "SDIV/MUL/SUB; kill x16, flags; zero guarded"; }
}
