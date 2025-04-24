package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// corresponds to slt sltu
public class SetRISC extends MachConcreteNode implements MachNode {
    final boolean _unsigned;
    public SetRISC( Node cmp, boolean unsigned ) { super(cmp); _unsigned=unsigned; }
    @Override public String op() { return "slt" + (_unsigned ? "u":""); }
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public void encoding( Encoding enc ) {
        riscv.r_type(enc,this,_unsigned ? 0b011 : 0b010,0);
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" < ").p(code.reg(in(2)));
    }
}