package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

public class MulIX86 extends MachConcreteNode implements MachNode {
    final int _imm;
    MulIX86( Node add, int imm ) {
        super(add);
        _inputs.pop();          // Pop the constant input
        _imm = imm;             // Record the constant
    }
    @Override public String op() { return "muli"; }
    @Override public String glabel() { return "*"; }
    @Override public RegMask regmap(int i) { return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    // Rare 3-address form
    @Override public final void encoding( Encoding enc ) {
        X86.imul(enc,enc.reg(this),enc.reg(in(1)),_imm);
    }
    // General form: "addi  dst += #imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" * #").p(_imm);
    }
}
