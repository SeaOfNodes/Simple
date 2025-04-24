package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class I2F8ARM extends MachConcreteNode implements MachNode {
    I2F8ARM(Node i2f8) { super(i2f8); }
    @Override public String op() { return "cvt"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.DMASK; }
    @Override public void encoding( Encoding enc ) {
        // SCVTF
        short self = (short)(enc.reg(this )-arm.D_OFFSET);
        short reg1 = enc.reg(in(1));
        int body = arm.float_cast(arm.OP_FLOAT_C, 1, reg1, self);
        enc.add4(body);
    }

    // General form: "i2f8 (flt)int_value"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p("(flt)").p(code.reg(in(1)));
    }

}
