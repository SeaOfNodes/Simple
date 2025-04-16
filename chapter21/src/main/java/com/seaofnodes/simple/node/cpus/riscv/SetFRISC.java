package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.BoolNode;
import com.seaofnodes.simple.node.MachConcreteNode;
import com.seaofnodes.simple.node.MachNode;

// corresponds to feq.d = equal
//                fle.d = less than or equal
//                flt.d = less than
public class SetFRISC extends MachConcreteNode implements MachNode {
    final String _bop;          // One of <,<=,==
    SetFRISC( BoolNode bool ) {
        super(bool);
        assert bool.isFloat();
        _bop = bool.op();
    }
    @Override public String op() { return "set"+_bop; }
    @Override public RegMask regmap(int i) { return riscv.FMASK; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public void encoding( Encoding enc ) {
        short dst  =         enc.reg(this );
        short src1 = (short)(enc.reg(in(1))-riscv.F_OFFSET);
        short src2 = (short)(enc.reg(in(2))-riscv.F_OFFSET);
        int body = riscv.r_type(riscv.OP_FP,dst,riscv.fsetop(_bop),src1,src2,0b1010001/*FCMP*/);
        enc.add4(body);
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" ").p(_bop).p(" ").p(code.reg(in(2)));
    }
}
