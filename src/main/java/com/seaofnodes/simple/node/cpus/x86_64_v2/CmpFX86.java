package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

public class CmpFX86 extends MachConcreteNode implements MachNode {
    CmpFX86( Node cmp ) { super(cmp); }
    @Override public String op() { return "cmpf"; }
    @Override public RegMask regmap(int i) { return x86_64_v2.XMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.FLAGS_MASK; }

    @Override public void encoding( Encoding enc ) {
        X86.sse(enc,0x66,0x0F2E,false,enc.reg(in(1))-x86_64_v2.XMM_OFFSET,enc.reg(in(2))-x86_64_v2.XMM_OFFSET);
    }

    // General form: "cmp src1,src2"
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="flags" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1))).p(", ").p(code.reg(in(2)));
    }
}
