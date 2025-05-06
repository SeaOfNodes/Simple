package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.isa.RiscV;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.SB;

// Convert a narrow C return register to Simple's full-width integer value.
public class ExtendRISC extends MachConcreteNode {
    final int _bits;
    final boolean _signed;
    ExtendRISC(ProjNode prj, TypeInteger ret) {
        super(prj);
        _inputs.set(0,null);
        _inputs.add(new ProjRISC(prj));
        _bits = 8 << ret.log_size();
        _signed = ret._min < 0;
    }
    @Override public String op() { return (_signed ? "sext" : "zext")+_bits; }
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public void encoding(Encoding enc) {
        short dst = enc.reg(this), src = enc.reg(in(1));
        int shift = 64-_bits;
        enc.add4(RiscV.i_type(RiscV.OP_IMM,dst,1,src,shift));
        enc.add4(RiscV.i_type(RiscV.OP_IMM,dst,5,dst,shift | (_signed ? 0x400 : 0)));
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1)));
    }
}
