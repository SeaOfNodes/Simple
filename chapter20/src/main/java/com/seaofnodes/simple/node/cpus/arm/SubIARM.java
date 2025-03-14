package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.node.MachConcreteNode;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class SubIARM extends MachConcreteNode implements MachNode {
    final TypeInteger _ti;
    SubIARM(Node sub, TypeInteger ti) {
        super(sub);
        _inputs.pop();
        _ti = ti;
    }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { return arm.RMASK; }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return arm.RMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // General form: "subi  rd = rs1 - imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" - #");
        _ti.print(sb);
    }

    @Override public String op() {
        return (_ti.value() == -1 ? "dec" : "subi");
    }

}
