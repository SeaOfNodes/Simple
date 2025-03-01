package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

// fcvt.d.w
// Converts a 32-bit signed integer, in integer register rs1 into a double-precision floating-point number in floating-point register rd.
public class I2F8RISC extends MachConcreteNode implements MachNode {
    I2F8RISC(Node i2f8) {super(i2f8);}

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1; return riscv.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.FMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        LRG frd_self = CodeGen.CODE._regAlloc.lrg(this);

        LRG rs1_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));

        int beforeSize = bytes.size();

        short reg_self = frd_self.get_reg();
        short reg1 = rs1_rg_1.get_reg();

        int body = riscv.r_fmt(riscv.R_FLOAT_CONVERSION, reg_self, riscv.RM.RNE, reg1, riscv.FMT.D, 0x1A);

        riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }

    // General form: "i2f8 (flt)int_value"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p("(flt)").p(code.reg(in(1)));
    }

    @Override public String op() { return "i2f8"; }
}
