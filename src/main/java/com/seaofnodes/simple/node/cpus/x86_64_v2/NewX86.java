package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import java.io.ByteArrayOutputStream;

public class NewX86 extends NewNode implements MachNode {

    // A pre-zeroed chunk of memory.
    NewX86( NewNode nnn ) {  super(nnn); }

    // Register mask allowed on input i, the size
    @Override public RegMask regmap(int i) {
        // Size
        if( i==2 ) return x86_64_v2.RDI_MASK;
        // Memory has no register
        return null;
    }
    @Override public RegMask outregmap() { throw Utils.TODO(); }

    // Register mask allowed as a result.  Pointer result in standard calling
    // convention.
    @Override public RegMask outregmap(int i) {
        if( i == 0 ) return x86_64_v2.RET_MASK;
        // Memory has no register
        return null;
    }

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
