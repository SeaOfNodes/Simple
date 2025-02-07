package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

// VCVT (between floating-point and integer)
public class I2F8ARM extends MachConcreteNode implements MachNode {
    I2F8ARM(Node i2f8) {super(i2f8);}
    @Override public String op() { return "i2f8"; }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1; return arm.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return arm.DMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // General form: "i2f8 (flt)int_value"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p("(flt)").p(code.reg(in(1)));
    }
}
