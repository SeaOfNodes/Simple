package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFunPtr;

public class NewX86 extends NewNode implements MachNode {
    // A pre-zeroed chunk of memory.
    NewX86( NewNode nnn ) { super(nnn); }
    @Override public String op() { return "alloc"; }
    // Size and pointer result in standard calling convention; null for all the
    // memory aliases edges
    @Override public RegMask regmap(int i) {
        // Any { int->int } signature will do, as they all pick the same registers
        return i==1 ? x86_64_v2.callInMask(TypeFunPtr.CALLOC,3) : null;
    }
    @Override public RegMask outregmap(int i) { return i == 1 ? x86_64_v2.RAX_MASK : null; }
    @Override public RegMask outregmap() { return null; }
    @Override public RegMask killmap() { return x86_64_v2.x86CallerSave(); }

    @Override public void encoding( Encoding enc ) {
        enc.external(this,"calloc");
        short r1 = x86_64_v2.callInMask(TypeFunPtr.CALLOC,2).firstReg();
        // ldi rcx,#1 // number of elements to calloc
        enc.add1(0xB8 + r1).add4(1);

        //enc.add1(x86_64_v2.rex(0, dst, 0));
        //// opcode; 0x81 or 0x83; 0x69 or 0x6B
        //enc.add1( opcode() + 2 );
        //enc.add1( x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, mod(), dst) );
        //// immediate(4 bytes) 32 bits or (1 byte)8 bits
        //enc.add1(_imm);


        // E8 cd    CALL rel32;
        enc.add1(0xE8);
        enc.add4(0);            // offset
    }

    // General form: "alloc #bytes"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p("#calloc, ").p(code.reg(size()));
    }

}
