package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.MachConcreteNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;

import java.io.ByteArrayOutputStream;

public class AndARM extends MachConcreteNode implements MachNode {
    AndARM(Node and) { super(and); }

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) { return arm.RMASK; }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return arm.RMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // General form
    // General form:  #rd = rs1 & rs2
    @Override public void asm(CodeGen code, SB sb){
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" & ").p(code.reg(in(2)));
    }
    @Override public String op() { return "and"; }
}
