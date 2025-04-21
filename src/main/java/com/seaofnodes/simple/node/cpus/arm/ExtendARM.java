package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.isa.Arm64;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.SB;

// Convert a narrow C return register to Simple's full-width integer value.
public class ExtendARM extends MachConcreteNode {
    final int _bits;
    final boolean _signed;
    ExtendARM(ProjNode prj, TypeInteger ret) {
        super(prj);
        _inputs.set(0,null);
        _inputs.add(new ProjARM(prj));
        _bits = 8 << ret.log_size();
        _signed = ret._min < 0;
    }
    @Override public String op() { return (_signed ? "sext" : "zext")+_bits; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.WMASK; }
    @Override public void encoding(Encoding enc) {
        short dst = enc.reg(this), src = enc.reg(in(1));
        // SBFM/UBFM aliases: sign/zero extend the low byte, short or int.
        enc.add4(Arm64.imm_shift(_signed ? Arm64.OPI_ASR : Arm64.OPI_LSR,0,_bits-1,src,dst));
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1)));
    }
}
