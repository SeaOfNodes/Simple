package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class DivX86 extends MachConcreteNode implements MachNode {
    DivX86( Node div ) { super(div); }

    // Todo: when idiv is used regalloc should not depend on rdx since its going to be sign extended
    // %11 = %8 / %9
    // %10 = mach_temp [RDX] // consuume rdx and it cannot be used
    // %11 = idiv %8, %9, %10
    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) {
        if(i == 1) return x86_64_v2.RET_MASK;
        if(i == 2) return x86_64_v2.DMASK; // no rdx in input
        throw Utils.TODO();
    }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
    // REX.W + F7 /7	IDIV r/m64
        LRG div_rg_1 = CodeGen.CODE._regAlloc.lrg(in(2));
        short reg1 = div_rg_1.get_reg();

        // sign extend rax before 128/64 bits division
        // sign extend rax to rdx:rax
        bytes.write(x86_64_v2.REX_W);
        // opcode for CQO
        bytes.write(0x99);

        // actual division starts
        int beforeSize = bytes.size();
        bytes.write(x86_64_v2.rex(0, reg1));
        bytes.write(0xF7); // opcode
        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0x07, reg1));

        return bytes.size() - beforeSize;
    }

    // General form: "div  dst /= src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" / ").p(code.reg(in(2)));
    }

    @Override public String op() { return "div"; }
}
