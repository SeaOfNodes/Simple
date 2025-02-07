package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import java.io.ByteArrayOutputStream;

public class NewRISC extends NewNode implements MachNode {
    // A pre-zeroed chunk of memory.
    NewRISC( NewNode nnn ) { super(nnn); }
    // Size and pointer result in standard calling convention; null for all the
    // memory and ordering edges
    @Override public RegMask    regmap(int i) { return i == 2 ? riscv.A0_MASK : null; }
    @Override public RegMask outregmap(int i) { return i == 0 ? riscv.A0_MASK : null; }
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // General form: "alloc #bytes"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(size()));
    }

    @Override public String op() { return "alloc"; }
}
