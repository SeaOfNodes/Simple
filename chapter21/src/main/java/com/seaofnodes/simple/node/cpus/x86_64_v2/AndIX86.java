package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;

public class AndIX86 extends MachConcreteNode implements MachNode {
    final TypeInteger _ti;
    AndIX86(Node and, TypeInteger ti) {
    super(and);
    _inputs.pop();
    _ti = ti;
    }

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.WMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }


    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // REX.W + 81 /4 id	AND r/m64, imm32
        // REX.W + 83 /4 ib	AND r/m64, imm8
        LRG and_rg_1 = CodeGen.CODE._regAlloc.lrg(this);

        short reg = and_rg_1.get_reg();
        int beforeSize = bytes.size();

        bytes.write(x86_64_v2.rex(0, reg, 0));
        // switch between int and short
        int imm32_8 = (int)_ti.value();

        // opcode
        int imm_size = x86_64_v2.imm_size(imm32_8);
        if(imm_size == 32) bytes.write(0x81);
        else if(imm_size == 8) bytes.write(0x83);

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0x04, reg));

        // immediate(4 bytes) 32 bits or (1 byte)8 bits
        x86_64_v2.imm(imm32_8, imm_size, bytes);

        return bytes.size() - beforeSize;
    }

    // General form
    // General form: "andi  dst & #imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" & #");
        _ti.print(sb);
    }

    @Override public String op() { return "andi"; }
}
