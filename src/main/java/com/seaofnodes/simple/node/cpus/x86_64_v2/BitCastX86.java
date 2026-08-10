package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

// A representation move, with no arithmetic, rounding, or flag changes.
public class BitCastX86 extends MachConcreteNode implements MachNode {
    private final boolean _toBits;
    BitCastX86(Intrinsic.BitCastNode cast) { super(cast); _toBits=cast.toBits(); }
    @Override public String op() { return _toBits ? "f64bits" : "f64frombits"; }
    @Override public RegMask regmap(int i) { assert i==1; return _toBits ? x86_64_v2.XMASK : x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return _toBits ? x86_64_v2.WMASK : x86_64_v2.XMASK; }
    @Override public void encoding(Encoding enc) {
        X86.move(enc,!_toBits,_toBits,
            enc.reg(this)-(_toBits ? 0 : x86_64_v2.XMM_OFFSET),
            enc.reg(in(1))-(_toBits ? x86_64_v2.XMM_OFFSET : 0));
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = bits ").p(code.reg(in(1)));
    }
}
