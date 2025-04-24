package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// mulh signed multiply instruction(no-imm form)
public class MulRISC extends MachConcreteNode implements MachNode{
    public MulRISC(Node mul) {super(mul);}
    @Override public String op() { return "mul"; }
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public void encoding( Encoding enc ) { riscv.r_type(enc,this,0,1);  }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" * ").p(code.reg(in(2)));
    }
}
