package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// corresponds to slt (and NOT sltu, slti, sltiu)
public class SetRISC extends MachConcreteNode implements MachNode {
    final String _bop;
    SetRISC( Node cmp, String bop) { super(cmp); _bop = bop;}
    @Override public String op() { return "set" ; }
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public void encoding( Encoding enc ) {
        short dst = enc.reg(this );
        short src = enc.reg(in(1));
        switch(_bop) {
            case "==":
                // sltiu rd, rs, 1
                enc.add4(riscv.i_type(riscv.OP_IMM, dst, 3, src,  1));
                break;
            case "<":
                riscv.r_type(enc,this,2,0);
                break;
            case "<=":
                riscv.r_type(enc,this,2,0);
                enc.add4(riscv.i_type(riscv.OP_IMM, src, 3, src,  1));
                break;
            default: throw Utils.TODO();
        }
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" < ").p(code.reg(in(2)));
    }
}
