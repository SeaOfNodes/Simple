package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.MachConcreteNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class MulIX86 extends MachConcreteNode  implements MachNode {
    final TypeInteger _ti;
    MulIX86(Node mul, TypeInteger ti) {super(mul); _inputs.pop(); _ti = ti;}

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) {
        // assert i==1;
        return x86_64_v2.WMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }


    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // REX.W + 69 /r id	IMUL r64, r/m64, imm32
        // REX.W + 6B /r ib	IMUL r64, r/m64, imm8
        LRG mul_rg = CodeGen.CODE._regAlloc.lrg(this);
        short reg1 = mul_rg.get_reg();

        int beforeSize = bytes.size();

        bytes.write(x86_64_v2.rex(reg1, reg1, 0));

        // immediate(4 bytes) 32 bits
        int imm32_8 = (int)_ti.value();

        // opcode
        int imm_size = x86_64_v2.imm_size(imm32_8);
        if(imm_size == 32) bytes.write(0x69);
        else if(imm_size == 8) bytes.write(0x6B);

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, reg1, reg1));

        // immediate(4 bytes) 32 bits or (1 byte)8 bits
        x86_64_v2.imm(imm32_8, imm_size, bytes);

        return bytes.size() - beforeSize;
    }
    // General form
    // General form: "muli  dst * #imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" * #");
        _ti.print(sb);
    }

    @Override public String op() { return "muli"; }
}
