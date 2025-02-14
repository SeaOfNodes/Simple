package com.seaofnodes.simple.node.cpus.arm;


import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;

import java.io.ByteArrayOutputStream;

public class NewARM extends NewNode implements MachNode {
    NewARM(NewNode nnn) {super(nnn);}
    // Register mask allowed on input i, the size
    @Override public RegMask regmap(int i) {
        // Size
        if( i==1 ) return arm.RET_MASK;
        // All the memory alias edges
        return null;
    }

    @Override public RegMask outregmap() { throw Utils.TODO(); }

    // Register mask allowed as a result.  Pointer result in standard calling
    // convention.
    @Override public RegMask outregmap(int i) {
        if( i == 1 ) return arm.RET_MASK;
        // All the memory aliases edges
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
