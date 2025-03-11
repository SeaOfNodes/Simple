package com.seaofnodes.simple.node.cpus.arm;


import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;

public class NewARM extends NewNode implements MachNode {
    // A pre-zeroed chunk of memory.
    NewARM(NewNode nnn) { super(nnn); }
    // Size and pointer result in standard calling convention; null for all the
    // memory aliases edges
    @Override public RegMask    regmap(int i) { return i == 1 ? arm. X0_MASK : null; }
    @Override public RegMask outregmap(int i) { return i == 1 ? arm. X0_MASK : null; }
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        enc.alloc(this);
        enc.add4(arm.b(37, 0));
    }

    // General form: "alloc #bytes"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(size()));
    }

    @Override public String op() { return "alloc"; }
}
