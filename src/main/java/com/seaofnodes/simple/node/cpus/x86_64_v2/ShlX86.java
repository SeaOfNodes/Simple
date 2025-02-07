package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import java.io.ByteArrayOutputStream;

public class ShlX86 extends MachConcreteNode implements MachNode {
    ShlX86(Node shl) {
        super(shl);
    };

    // Register mask allowed on input i.
    // This is the normal calling convention

    // CL register must be used for input(2)
    @Override public RegMask regmap(int i) {
        if(i == 1) return x86_64_v2.WMASK;
        if(i == 2) return x86_64_v2.RCX_MASK;
        throw Utils.TODO();
    }

    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }

    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // General form
    // General form: "shl  dst << src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1)));
    }

    @Override public String op() { return "shl"; }
}
