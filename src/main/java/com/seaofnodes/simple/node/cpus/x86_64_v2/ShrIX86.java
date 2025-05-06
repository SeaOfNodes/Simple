package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.util.SB;

public class ShrIX86 extends MachConcreteNode implements MachNode {
    final int _imm;
    ShrIX86( Node shri, int imm ) { super(shri); assert x86_64_v2.imm8(imm); _inputs.pop(); _imm = imm; }
    @Override public String op() { return "shri"; }
    @Override public String glabel() { return ">>>"; }
    int opcode() { return 0xC1; }
    int mod() { return 5; }

    @Override public RegMask regmap(int i) { return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    @Override public int twoAddress() { return 1; }

    @Override public void encoding(Encoding enc) {
        X86.shift(enc,mod(),enc.reg(this),_imm);
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" ").p(glabel()).p("= #").p(_imm);
    }
}
