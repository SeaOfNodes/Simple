package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class AddIX86 extends MachConcreteNode implements MachNode {
    final TypeInteger _ti;
    AddIX86( Node add, TypeInteger ti ) {
        super(add);
        _inputs.pop();
         _ti = ti;
    }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) {
        // assert i== i;
        return x86_64_v2.WMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // REX.W + 81 /0 id	ADD r/m64, imm32
        // || REX.W + 83 /0 ib
        LRG add_rg = CodeGen.CODE._regAlloc.lrg(this);

        short reg = add_rg.get_reg();
        int beforeSize = bytes.size();

        bytes.write(x86_64_v2.rex(0, reg, 0));

        // switch between int and short
        int imm32_8 = (int)_ti.value();

        // opcode
        int imm_size = x86_64_v2.imm_size(imm32_8);
        if(imm_size == 32) bytes.write(0x81);
        else if(imm_size == 8) bytes.write(0x83);

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0, reg));

        // immediate(4 bytes) 32 bits or (1 byte)8 bits
        x86_64_v2.imm(imm32_8, imm_size, bytes);
        return bytes.size() - beforeSize;
    }

    // General form: "addi  dst += #imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" + #");
        _ti.print(sb);
    }

    @Override public String op() {
        return _ti.value() == 1  ? "inc" : (_ti.value() == -1 ? "dec" : "addi");
    }
}
