package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.isa.RiscV;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class DivRISC extends MachConcreteNode implements MachNode{
    private final boolean _mod;
    public DivRISC(Node div) {super(div); _mod=div instanceof ModNode;}
    @Override public String op() { return _mod ? "mod" : "div"; }
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public void encoding( Encoding enc ) {
        // RISC-V returns -1 for DIV by zero, and the dividend for REM by zero.
        // Both ideal operations instead return zero.
        enc.add4(RiscV.b_type(RiscV.OP_BRANCH,1,enc.reg(in(2)),(short)riscv.ZERO,12));
        enc.add4(RiscV.i_type(RiscV.OP_IMM,enc.reg(this),0,riscv.ZERO,0));
        enc.add4(RiscV.j_type(RiscV.OP_JAL,riscv.ZERO,8));
        riscv.r_type(enc,this,_mod ? 6 : 4,1);
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(_mod ? " % " : " / ").p(code.reg(in(2)));
    }

}
