package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// Right Shift Arithmetic
public class SraRISC extends MachConcreteNode implements MachNode {
    public SraRISC(Node sra) { super(sra); }
    @Override public String op() { return "sar"; }
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public void encoding( Encoding enc ) { riscv.r_type(enc,this,0b101,0b00100000);  }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" >> ").p(code.reg(in(2)));
    }
}
