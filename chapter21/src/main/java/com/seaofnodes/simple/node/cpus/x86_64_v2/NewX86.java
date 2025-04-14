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
    @Override public RegMask regmap(int i) { return i==1 ? x86_64_v2.callInMask(TypeFunPtr.CALLOC,2) : null; }
    @Override public RegMask outregmap(int i) { return i == 1 ? x86_64_v2.RAX_MASK : null; }
    @Override public RegMask outregmap() { return null; }
    @Override public RegMask killmap() { return x86_64_v2.x86CallerSave(); }

    @Override public void encoding( Encoding enc ) {
        enc.external(this,"calloc");
        // This has to call the *native* ABI, regardless of how Simple is
        // being compiled, because it links against the native calloc.
        // ldi rcx,#1 // number of elements to calloc
        RegMask ARG3 = x86_64_v2.callInMask(TypeFunPtr.CALLOC,3);
        enc.add1(0xB8 + ARG3.firstReg()).add4(1);
        // E8 cd    CALL rel32;
        enc.add1(0xE8);
        enc.add4(0);            // offset
    }

    // General form: "alloc #bytes"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p("#calloc, ").p(code.reg(size()));
    }

}
