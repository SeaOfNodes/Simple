package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

public class NotX86 extends MachConcreteNode implements MachNode {
    NotX86(NotNode not) { super(not); }
    @Override public String op() { return "not"; }
    @Override public RegMask regmap(int i) { return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.RMASK;  }
    @Override public RegMask killmap() { return x86_64_v2.FLAGS_MASK; }

    @Override public void encoding( Encoding enc ) {
        assert !(in(1) instanceof NotNode); // Cleared out by peeps
        assert !(in(1) instanceof BoolNode);
        X86.not(enc,enc.reg(this),enc.reg(in(1)));
    }
    @Override public void asm(CodeGen code, SB sb) { sb.p(code.reg(this)); }
}
