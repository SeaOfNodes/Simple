package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class NegRISC extends MachConcreteNode implements MachNode {
    public NegRISC( Node neg ) { super(neg); }
    @Override public String op() { return "neg"; }
    @Override public RegMask regmap(int i) { assert i==1; return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public void encoding( Encoding enc ) {
        short dst = enc.reg(this );
        short src = enc.reg(in(1));
        riscv.r_type(riscv.OP,dst,0,0,src,0x20);
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = -").p(code.reg(in(1)));
    }
}
