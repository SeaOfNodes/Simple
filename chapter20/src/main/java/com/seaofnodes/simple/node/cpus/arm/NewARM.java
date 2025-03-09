package com.seaofnodes.simple.node.cpus.arm;


import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;

import java.io.ByteArrayOutputStream;

public class NewARM extends NewNode implements MachNode {
    // A pre-zeroed chunk of memory.
    NewARM(NewNode nnn) { super(nnn); }
    // Size and pointer result in standard calling convention; null for all the
    // memory aliases edges
    @Override public RegMask    regmap(int i) { return i == 1 ? arm. X0_MASK : null; }
    @Override public RegMask outregmap(int i) { return i == 1 ? arm. X0_MASK : null; }
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // mov x0, size
        // bl
        // Todo: relocs
        int beforeSize = bytes.size();
        int body = arm.b(37, 0);
        arm.push_4_bytes(body, bytes);
        return bytes.size() - beforeSize;
    }

    // General form: "alloc #bytes"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(size()));
    }

    @Override public String op() { return "alloc"; }
}
