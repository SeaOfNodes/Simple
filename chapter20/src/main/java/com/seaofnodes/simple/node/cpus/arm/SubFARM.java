package com.seaofnodes.simple.node.cpus.arm;


import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class SubFARM extends MachConcreteNode implements MachNode{
    SubFARM(Node subf) {super(subf);}

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return arm.DMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return arm.DMASK; }
    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // Default on double precision for now(64 bits)
    // General form: "vsub.f32  rd = src1 + src2
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" - ").p(code.reg(in(2)));
    }

    @Override public String op() { return "vsub"; }

}
