package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.isa.RiscV;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

// A representation move, with no arithmetic, rounding, or flag changes.
public class BitCastRISC extends MachConcreteNode implements MachNode {
    private final boolean _toBits;
    BitCastRISC(Intrinsic.BitCastNode cast) { super(cast); _toBits=cast.toBits(); }
    @Override public String op() { return _toBits ? "f64bits" : "f64frombits"; }
    @Override public RegMask regmap(int i) { assert i==1; return _toBits ? riscv.FMASK : riscv.RMASK; }
    @Override public RegMask outregmap() { return _toBits ? riscv.WMASK : riscv.FMASK; }
    @Override public void encoding(Encoding enc) {
        enc.add4(RiscV.r_type(RiscV.OP_FP,
            enc.reg(this)-(_toBits ? 0 : riscv.F_OFFSET),0,
            enc.reg(in(1))-(_toBits ? riscv.F_OFFSET : 0),0,_toBits ? 0b1110001 : 0b1111001));
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = bits ").p(code.reg(in(1)));
    }
}
