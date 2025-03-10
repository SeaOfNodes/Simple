package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;


// unconditional jump
// E9 cd	JMP rel32
public class UJmpX86 implements MachNode {
    UJmpX86() {
    }

    @Override public String op() { return "UJmp"; }

    @Override public RegMask regmap(int i) {return null; }
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
           // E9 cd	JMP rel32
        int beforeSize = bytes.size();
        bytes.write(0xE9);
        x86_64_v2.imm(0, 32, bytes);

        return bytes.size() - beforeSize;
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p("UJump");
    }
}
