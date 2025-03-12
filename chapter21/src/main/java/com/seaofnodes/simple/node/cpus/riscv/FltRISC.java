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
    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        short dst = (short)(enc.reg(this) - riscv.F_OFFSET);
        double d = ((TypeFloat)_con).value();
        long x = Double.doubleToRawLongBits(d);

        // Encode as either a memory slot and load, or stuff into an integer
        // register (probably 2 int ops), then a fmv.w.x so 3 ops.

        //int body = riscv.i_type(0x07, dst, 0X03, fpr_reg - riscv.F_OFFSET, x);
        //enc.push_add4(body);
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" #"));
    }

}
