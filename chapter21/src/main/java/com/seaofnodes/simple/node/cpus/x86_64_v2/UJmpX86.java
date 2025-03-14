package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// unconditional jump
// E9 cd	JMP rel32
public class UJmpX86 implements MachNode {
    UJmpX86() { }
    @Override public String op() { return "jmp"; }
    @Override public RegMask regmap(int i) {return null; }
    @Override public RegMask outregmap() { return null; }
    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        //enc.jump();
        //// E9 cd	JMP rel32
        //enc.add1(0xE9);
        //enc.add4(0);
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) { }
}
