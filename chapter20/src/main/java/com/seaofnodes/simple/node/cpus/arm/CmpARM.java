package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import java.io.ByteArrayOutputStream;


public class CmpARM extends MachConcreteNode implements MachNode{
    CmpARM( Node cmp ) { super(cmp); }


    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // SUBS (shifted register)
        // subs	w8, w8, w9
        LRG subs_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG subs_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG subs_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short self = subs_self.get_reg();
        short reg1 = subs_rg_1.get_reg();
        short reg2 = subs_rg_2.get_reg();

        int beforeSize = bytes.size();
        // self = reg1
        int body = arm.r_reg(235, 0, reg2,  0, reg1, self);
        arm.push_4_bytes(body, bytes);
        return bytes.size() - beforeSize;
    }

    // General form: "cmp  rs1, rs2"
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="flags" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1))).p(", ").p(code.reg(in(2)));
    }

    @Override public RegMask regmap(int i) { assert i==1 || i==2; return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.FLAGS_MASK; }

    @Override public String op() { return "cmp"; }
}
