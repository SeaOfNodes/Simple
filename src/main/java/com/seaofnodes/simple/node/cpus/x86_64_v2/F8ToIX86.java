package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class F8ToIX86 extends MachConcreteNode implements MachNode {
    F8ToIX86(Node f8toi ) { super(f8toi); }
    @Override public String op() { return "cvti"; }
    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.XMASK; }
    @Override public RegMask killmap() { return x86_64_v2.FLAGS_MASK; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }

    @Override public void encoding( Encoding enc ) {
        X86.floatToInteger(enc,enc.reg(this),enc.reg(in(1))-x86_64_v2.XMM_OFFSET);
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p("(int)").p(code.reg(in(1)));
    }
}
