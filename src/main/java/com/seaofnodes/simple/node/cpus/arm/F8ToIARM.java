package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

// VCVT (between floating-point and integer)
public class F8ToIARM extends MachConcreteNode implements MachNode {
    F8ToIARM(Node f8toi) {super(f8toi);}
    @Override public String op() { return "f8toi"; }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1; return arm.DMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return arm.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p("(int)").p(code.reg(in(1)));
    }
}
