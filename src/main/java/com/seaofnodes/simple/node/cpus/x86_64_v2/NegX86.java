package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

public class NegX86 extends MachConcreteNode implements MachNode {
    NegX86(MinusNode not) { super(not); }
    @Override public String op() { return "neg"; }
    @Override public RegMask regmap(int i) { return x86_64_v2.RMASK; }
    @Override public RegMask outregmap()   { return x86_64_v2.RMASK; }
    @Override public RegMask killmap()     { return x86_64_v2.FLAGS_MASK; }
    @Override public int twoAddress() { return 1; }

    @Override public void encoding( Encoding enc ) {
        X86.unary(enc,0xF7,3,enc.reg(this));
    }
    @Override public void asm(CodeGen code, SB sb) { sb.p(code.reg(this)); }
}
