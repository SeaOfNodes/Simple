package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.isa.Arm64;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

// A representation move, with no arithmetic, rounding, or flag changes.
public class BitCastARM extends MachConcreteNode implements MachNode {
    private final boolean _toBits;
    BitCastARM(Intrinsic.BitCastNode cast) { super(cast); _toBits=cast.toBits(); }
    @Override public String op() { return _toBits ? "f64bits" : "f64frombits"; }
    @Override public RegMask regmap(int i) { assert i==1; return _toBits ? arm.DMASK : arm.RMASK; }
    @Override public RegMask outregmap() { return _toBits ? arm.WMASK : arm.DMASK; }
    @Override public void encoding(Encoding enc) {
        enc.add4(Arm64.f_mov_general(Arm64.OP_FMOV,0b01,0,_toBits ? 0b110 : 0b111,
            enc.reg(in(1))-(_toBits ? arm.D_OFFSET : 0),
            enc.reg(this)-(_toBits ? 0 : arm.D_OFFSET)));
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = bits ").p(code.reg(in(1)));
    }
}
