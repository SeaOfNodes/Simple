package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.TypeFloat;

public class FltRISC extends ConstantNode implements MachNode{
    FltRISC(ConstantNode con) { super(con); }
    @Override public String op() { return "flw"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return riscv.FMASK; }
    @Override public boolean isClone() { return true; }
    @Override public FltRISC copy() { return new FltRISC(this); }

    @Override public void encoding( Encoding enc ) {
        enc.largeConstant(this,_con);
        short dst = (short)(enc.reg(this) - riscv.F_OFFSET);
        // AUIPC dst,#hi20_constant_pool
        enc.add4(riscv.u_type(0b0010111, dst, 0));
        // Load dst,[dst+#low12_constant_pool]
        enc.add4(riscv.i_type(0x07, dst, 0x03, dst, 0));
    }

    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" #"));
    }

}
