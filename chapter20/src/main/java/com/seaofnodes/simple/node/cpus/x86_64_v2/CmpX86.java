package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class CmpX86 extends MachConcreteNode implements MachNode {
    CmpX86( Node cmp ) { super(cmp); }

    @Override public RegMask regmap(int i) { assert i==1 || i==2; return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.FLAGS_MASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // REX.W + 39 /r	CMP r/m64,r64
        LRG cmp_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG cmp_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short reg1 = cmp_rg_1.get_reg();
        short reg2 = cmp_rg_2.get_reg();

        int beforeSize = bytes.size();
        bytes.write(x86_64_v2.rex(reg1, reg2, 0));
        bytes.write(0x3B); // opcode

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, reg1, reg2));

        return bytes.size() - beforeSize;
    }

    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="FLAGS" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1))).p(", ").p(code.reg(in(2)));
    }

    @Override public String op() { return "cmp"; }
}
