package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.util.SB;

// Arithmetic Right Shift
public class SarIX86 extends MachConcreteNode implements MachNode {
    final int _imm;
    SarIX86( Node sari, int imm ) { super(sari); assert x86_64_v2.imm8(imm); _inputs.pop(); _imm = imm; }
    @Override public String op() { return "sari"; }
    @Override public String glabel() { return ">>"; }
    int opcode() { return 0xC1; }
    int mod() { return 7; }

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
