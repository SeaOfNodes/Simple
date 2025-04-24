package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.codegen.*;

public class MulIX86 extends MachConcreteNode implements MachNode {
    final int _imm;
    MulIX86( Node mul, int imm ) {
        super(mul);
        _inputs.pop();          // Pop the constant input
        _imm = imm;             // Record the constant
    }
    @Override public String op() { return "muli"; }
    @Override public String glabel() { return "*"; }
    @Override public RegMask regmap(int i) { return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    // Retain Chapter 21's allocation contract: output is also input #1.
    @Override public int twoAddress() { return 1; }

    // IMUL encodes a destination register, unlike ImmX86's opcode extension.
    @Override public final void encoding( Encoding enc ) {
        X86.imul(enc,enc.reg(this),enc.reg(in(1)),_imm);
    }
    // General form: "muli  dst = src * #imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" * #").p(_imm);
    }
}
