package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;

public class ShrIX86 extends MachConcreteNode implements MachNode{
    final TypeInteger _ti;
    ShrIX86(Node shr, TypeInteger ti) {super(shr); _inputs.pop(); _ti = ti;}

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) {
        //assert i==1;
        return x86_64_v2.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }

    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // REX.W + C1 /5 ib	SHR r/m64, imm8
        LRG shr_lrg = CodeGen.CODE._regAlloc.lrg(this);
        short reg = shr_lrg.get_reg();

        int beforeSize = bytes.size();

        bytes.write(x86_64_v2.rex(0, reg, 0));
        bytes.write(0xC1); // opcode

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0x05, reg));

        // immediate(4 bytes) 32 bits
        int imm8 = (int)_ti.value();
        x86_64_v2.imm(imm8, 8, bytes);

        return bytes.size() - beforeSize;
    }


    // General form
    // General form: "shri  dst >>> #imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" >>> #");
        _ti.print(sb);
    }

    @Override public String op() { return "shri"; }
}
