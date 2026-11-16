package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

public class I2f8X86 extends MachConcreteNode implements MachNode {
    I2f8X86(Node i2f8 ) { super(i2f8); }
    @Override public String op() { return "cvtf"; }
    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.WMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }

    @Override public void encoding( Encoding enc ) {
        X86.sse(enc,0xF2,0x0F2A,true,enc.reg(this)-x86_64_v2.XMM_OFFSET,enc.reg(in(1)));
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p("(flt)").p(code.reg(in(1)));
    }
}
