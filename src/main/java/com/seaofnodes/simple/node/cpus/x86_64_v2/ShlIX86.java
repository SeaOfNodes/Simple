package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.node.Node;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class ShlIX86 extends MachConcreteNode implements MachNode {
    final int _imm;
    ShlIX86( Node shli, int imm ) {
        super(shli);
        assert x86_64_v2.imm8(imm);
        _inputs.pop();          // Pop constant input off
        _imm = imm;
    }
    public int opcode() { return 0xC1; }
    public int mod() { return 4; }

    @Override public RegMask regmap(int i) { return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    @Override public int twoAddress() { return 1; }

    @Override public String op() { return "shli"; }
    @Override public String glabel() { return "<<"; }
    @Override public void encoding(Encoding enc) {
        X86.shift(enc,mod(),enc.reg(this),_imm);
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" ").p(glabel()).p("= #").p(_imm);
    }
}
