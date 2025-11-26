package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;
import java.util.BitSet;
import java.lang.StringBuilder;

// Arithmetic Shift Right (register)
public class AsrARM extends MachConcreteNode implements MachNode {
    AsrARM(Node asr) {super(asr);}
    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) {
        // assert i==1;
        return arm.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return arm.RMASK; }

    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // General form
    // General form: "asr rd, rs1, rs2"
    @Override public void asm(CodeGen code, SB sb) {

        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" >> ").p(code.reg(in(2)));
    }

    @Override public String op() { return "asr"; }
}
