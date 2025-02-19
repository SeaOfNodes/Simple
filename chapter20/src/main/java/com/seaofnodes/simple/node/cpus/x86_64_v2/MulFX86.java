package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFloat;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class MulFX86 extends MachConcreteNode implements MachNode {
    MulFX86( Node mulf) { super(mulf); }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return x86_64_v2.XMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }
    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }
    // Ok to swap arguments
    @Override public boolean commutes() { return true; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // F2 0F 59 /r MULSD xmm1,xmm2/m64
        LRG mul_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG mul_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short reg1 = mul_rg_1.get_reg();
        short reg2 = mul_rg_2.get_reg();

        int beforeSize = bytes.size();
        bytes.write(x86_64_v2.rex(reg1 - x86_64_v2.FLOAT_OFFSET, reg2 - x86_64_v2.FLOAT_OFFSET));

        // Fopcode
        bytes.write(0xF2);
        bytes.write(0x0F);
        bytes.write(0x59);

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, reg1 - x86_64_v2.FLOAT_OFFSET , reg2 - x86_64_v2.FLOAT_OFFSET));

        return bytes.size() - beforeSize;
    }

    // General form: "mulf  dst *= src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" * ").p(code.reg(in(2)));
    }

    @Override public String op() { return "mulf"; }
}
