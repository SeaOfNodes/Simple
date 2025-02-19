package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class I2f8X86 extends MachConcreteNode implements MachNode {
    I2f8X86(Node i2f8 ) { super(i2f8); }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.WMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // F2 0F 2A /r CVTSI2SD xmm1, r32/m32
        LRG self = CodeGen.CODE._regAlloc.lrg(this);
        LRG from = CodeGen.CODE._regAlloc.lrg(in(1));

        short reg1 = self.get_reg();
        short reg2 = from.get_reg();

        int beforeSize = bytes.size();

        // Fopcode
        bytes.write(0xF2);
        bytes.write(x86_64_v2.rex(reg1 - 16, reg2));
        bytes.write(0x0F);
        bytes.write(0x2A);

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, reg1 - 16 , reg2));
        return bytes.size() - beforeSize;
    }

    // General form: "i2f8 (flt)int_value"

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p("(flt)").p(code.reg(in(1)));
    }

    @Override public String op() { return "i2f8"; }
}
