package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import java.io.ByteArrayOutputStream;

// compare instruction on float regs input(D-registers)
public class CmpFARM extends MachConcreteNode implements MachNode{
    CmpFARM(Node cmp) {super(cmp);}

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // General form: "cmp  d0, d1"
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="flags" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1))).p(", ").p(code.reg(in(2)));
    }

    @Override public RegMask regmap(int i) { assert i==1 || i==2; return arm.DMASK; }
    @Override public RegMask outregmap() { return arm.FLAGS_MASK; }

    @Override public String op() { return "cmpf"; }
}
