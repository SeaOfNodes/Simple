package com.seaofnodes.simple.node.cpus.x86_64_v2;


import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class CallX86 extends MachConcreteNode implements MachNode {
    Node callN;
    CallX86(Node call) {
        super(call);
        callN = call;

    }
    // Calling convention here?
    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return RegMask.EMPTY; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        sb.p(dst).p("    ").p(code.reg(callN));
    }

    @Override public String op() { return "call"; }

}
