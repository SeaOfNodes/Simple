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
        short dst = (short)(enc.reg(this) - riscv.F_OFFSET);
        double d = ((TypeFloat)_con).value();
        long x = Double.doubleToRawLongBits(d);

        // Encode as ei ther a memory slot and load, or stuff into an integer
        // register (probably 2 int ops), then a fmv.w.x so 3 ops.
        // auipc  t0,0
        int auipc = riscv.u_type(0b0010111, dst, 0);
        // fld     reg, reg, 0
        int fld = riscv.i_type(0x07, dst - riscv.F_OFFSET, 0X03, dst - riscv.F_OFFSET, 0);
        enc.add4(auipc);
        enc.add4(fld);

    }

    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" #"));
    }

}
