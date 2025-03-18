package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class NewX86 extends NewNode implements MachNode {
    // A pre-zeroed chunk of memory.
    NewX86( NewNode nnn ) { super(nnn); }
    @Override public String op() { return "alloc"; }
    // Size and pointer result in standard calling convention; null for all the
    // memory aliases edges
    @Override public RegMask    regmap(int i) { return i == 1 ? x86_64_v2.RDI_MASK : null; }
    @Override public RegMask outregmap(int i) { return i == 1 ? x86_64_v2.RAX_MASK : null; }
    @Override public RegMask outregmap() { return null; }
    @Override public RegMask killmap() { return x86_64_v2.x86CallerSave(); }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        enc.external(this,"calloc");
        // E8 cd    CALL rel32;
        enc.add1(0xE8);
        enc.add4(0);            // offset
    }

    // General form: "alloc #bytes"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(size()));
    }

}
