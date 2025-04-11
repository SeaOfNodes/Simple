package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFunPtr;

public class NewX86 extends NewNode implements MachNode {
    public static final String OS  = System.getProperty("os.name");
    public static final RegMask ARG2 = OS.startsWith("Windows")
        ? x86_64_v2.callWin64(TypeFunPtr.CALLOC,2)
        : x86_64_v2.callSys5 (TypeFunPtr.CALLOC,2);
    public static final RegMask ARG3 = OS.startsWith("Windows")
        ? x86_64_v2.callWin64(TypeFunPtr.CALLOC,3)
        : x86_64_v2.callSys5 (TypeFunPtr.CALLOC,3);

    // A pre-zeroed chunk of memory.
    NewX86( NewNode nnn ) { super(nnn); }
    @Override public String op() { return "alloc"; }
    // Size and pointer result in standard calling convention; null for all the
    // memory aliases edges
    @Override public RegMask regmap(int i) {
        return i==1 ? ARG2 : null;
    }
    @Override public RegMask outregmap(int i) { return i == 1 ? x86_64_v2.RAX_MASK : null; }
    @Override public RegMask outregmap() { return null; }
    @Override public RegMask killmap() { return x86_64_v2.x86CallerSave(); }

    @Override public void encoding( Encoding enc ) {
        enc.external(this,"calloc");
        // This has to call the *native* ABI, irregardless of how Simple is
        // being compiled, because it links against the native calloc.
        // ldi rcx,#1 // number of elements to calloc
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
