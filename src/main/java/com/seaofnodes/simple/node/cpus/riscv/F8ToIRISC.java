package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.x86_64_v2.x86_64_v2;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class F8ToIRISC extends MachConcreteNode implements MachNode {
    F8ToIRISC(Node f8toi) {super(f8toi);}

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1; return riscv.FMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.RMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p("(int)").p(code.reg(in(1)));
    }

    @Override public String op() { return "f8toi"; }
}
