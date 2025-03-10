package com.seaofnodes.simple.node.cpus.arm;


import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;


public class I2F8ARM extends MachConcreteNode implements MachNode {
    I2F8ARM(Node i2f8) {super(i2f8);}

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1; return arm.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return arm.DMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // SCVTF

        LRG frd_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG rs1_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));

        short reg_self = frd_self.get_reg();
        short reg1 = rs1_rg_1.get_reg();

        int beforeSize = bytes.size();
        int body = arm.float_cast(158, 1, reg1, reg_self - arm.D_OFFSET);

        riscv.push_4_bytes(body, bytes);
        return bytes.size() - beforeSize;
    }

    // General form: "i2f8 (flt)int_value"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p("(flt)").p(code.reg(in(1)));
    }

    @Override public String op() { return "i2f8"; }
}
