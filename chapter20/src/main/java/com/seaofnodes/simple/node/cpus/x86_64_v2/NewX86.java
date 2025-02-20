package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import java.io.ByteArrayOutputStream;

public class NewX86 extends NewNode implements MachNode {

    // A pre-zeroed chunk of memory.
    NewX86( NewNode nnn ) {  super(nnn); }

    // Register mask allowed on input i, the size
    @Override public RegMask regmap(int i) {
        // Size
        if( i==1 ) return x86_64_v2.RDI_MASK;
        // All the memory alias edges
        return null;
    }
    @Override public RegMask outregmap() { return x86_64_v2.RET_MASK; }

    // Register mask allowed as a result.  Pointer result in standard calling
    // convention.
    @Override public RegMask outregmap(int i) {
        if( i == 1 ) return x86_64_v2.RET_MASK;
        // All the memory aliases edges
        return null;
    }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // E8 cd    CALL rel32;

        int beforeSize = bytes.size();

        bytes.write(0xE8);
        bytes.write(x86_64_v2.imm(0, 32, bytes)); //offset

        return bytes.size() - beforeSize;
    }

    // General form: "alloc #bytes"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(size()));
    }

    @Override public String op() { return "alloc"; }
}
