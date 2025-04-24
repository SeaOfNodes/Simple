package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public abstract class RegX86 extends MachConcreteNode {
    RegX86( Node add ) { super(add); }
    @Override public RegMask regmap(int i) { return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    @Override public int twoAddress() { return 1; }
    abstract int opcode();
    @Override public void encoding( Encoding enc ) {
        X86.reg(enc,opcode(),enc.reg(in(1)),enc.reg(in(2)));
    }
    // General form: "add  dst += src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" ").p(glabel()).p("= ").p(code.reg(in(2)));
    }
}
