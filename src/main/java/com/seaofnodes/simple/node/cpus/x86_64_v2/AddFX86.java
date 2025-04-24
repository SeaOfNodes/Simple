package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class AddFX86 extends MachConcreteNode implements MachNode {
    AddFX86( Node addf ) { super(addf); }
    @Override public String op() { return "addf"; }
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return x86_64_v2.XMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }
    @Override public int twoAddress() { return 1; }
    @Override public boolean commutes() { return true; }

    @Override public void encoding( Encoding enc ) {
        X86.sse(enc,0xF2,0x0F58,false,enc.reg(this)-x86_64_v2.XMM_OFFSET,enc.reg(in(2))-x86_64_v2.XMM_OFFSET);
    }

    // General form: "addf  dst += src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" += ").p(code.reg(in(2)));
    }
}
