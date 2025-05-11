package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;

import com.seaofnodes.simple.node.*;

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
        short dst = enc.reg(this); // Also src1
        enc.add1(x86_64_v2.rex(0, dst, 0));
        enc.add1( opcode());

        enc.add1( x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, mod(), dst) );

        // immediate(4 bytes) 32 bits or (1 byte)8 bits
        if( x86_64_v2.imm8(_imm) ) enc.add1(_imm);
        else                       enc.add4(_imm);
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" ").p(glabel()).p("= #").p(_imm);
    }
}
