package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class SplitX86 extends MachConcreteNode implements MachNode {
    SplitX86( ) { super(new Node[2]); }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { return x86_64_v2.SPLIT_MASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.SPLIT_MASK; }

    @Override public boolean isSplit() { return true; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // General form: "mov  dst = src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1)));
    }

    @Override public String op() { return "mov"; }
}
