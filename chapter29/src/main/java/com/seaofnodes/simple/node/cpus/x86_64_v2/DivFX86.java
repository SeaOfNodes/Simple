package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

public class DivFX86 extends MachConcreteNode implements MachNode {
    DivFX86( Node divf) { super(divf); }
    @Override public String op() { return "divf"; }
    @Override public RegMask regmap(int i) { return x86_64_v2.XMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }
    @Override public int twoAddress() { return 1; }

    @Override public void encoding( Encoding enc ) {
        X86.sse(enc,0xF2,0x0F5E,false,enc.reg(this)-x86_64_v2.XMM_OFFSET,enc.reg(in(2))-x86_64_v2.XMM_OFFSET);
    }


    // General form: "divf  dst /= src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" /= ").p(code.reg(in(2)));
    }
}
