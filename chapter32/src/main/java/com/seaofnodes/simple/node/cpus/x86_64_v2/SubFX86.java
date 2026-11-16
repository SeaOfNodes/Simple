package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

public class SubFX86 extends MachConcreteNode implements MachNode {
    SubFX86( Node subf) {  super(subf); }
    @Override public String op() { return "subf"; }
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return x86_64_v2.XMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }
    @Override public int twoAddress() { return 1; }

    @Override public void encoding( Encoding enc ) {
        X86.sse(enc,0xF2,0x0F5C,false,enc.reg(this)-x86_64_v2.XMM_OFFSET,enc.reg(in(2))-x86_64_v2.XMM_OFFSET);
    }

    // General form: "subf  dst -= src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" -= ").p(code.reg(in(2)));
    }
}
