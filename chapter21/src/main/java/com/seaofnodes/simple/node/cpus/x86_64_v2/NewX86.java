package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import java.io.ByteArrayOutputStream;

public class NewX86 extends NewNode implements MachNode {
    // A pre-zeroed chunk of memory.
    NewX86( NewNode nnn ) { super(nnn); }
    // Size and pointer result in standard calling convention; null for all the
    // memory aliases edges
    @Override public RegMask    regmap(int i) { return i == 1 ? x86_64_v2.RDI_MASK : null; }
    @Override public RegMask outregmap(int i) { return i == 1 ? x86_64_v2.RAX_MASK : null; }
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // E8 cd    CALL rel32;
        int beforeSize = bytes.size();

        bytes.write(0xE8);
        x86_64_v2.imm(0, 32, bytes); //offset

        return bytes.size() - beforeSize;
    }

    // General form: "alloc #bytes"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(size()));
    }

    @Override public String op() { return "alloc"; }
}
