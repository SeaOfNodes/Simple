package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import java.io.ByteArrayOutputStream;

public class CmpARM extends MachConcreteNode implements MachNode {
    CmpARM( Node cmp ) { super(cmp); }
    @Override public String op() { return "cmp"; }


    @Override public RegMask regmap(int i) { assert i==1 || i==2; return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.FLAGS_MASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // General form: "cmp  rs1, rs2"
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="flags" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1))).p(", ").p(code.reg(in(2)));
    }
}
