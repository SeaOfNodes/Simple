package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.SB;

// Load upper 20bits.
public class LUI extends ConstantNode implements MachNode {
    public LUI( int imm20 ) {
        super(TypeInteger.constant(imm20));
        assert (imm20 & 0xFFF)==0;
    }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public boolean isClone() { return true; }
    @Override public LUI copy() { return new LUI((int)((TypeInteger)_con).value()); }
    @Override public String op() { return "lui"; }
    @Override public void encoding( Encoding enc ) {
        long x = ((TypeInteger)_con).value();
        int imm20 = (int)(x>>12) & 0xFFFFF;
        short dst = enc.reg(this);
        int lui = riscv.u_type(riscv.OP_LUI, dst, imm20);
        enc.add4(lui);
    }

    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        sb.p(reg).p(" = #").hex4((int)(((TypeInteger)_con).value()));
    }
}
