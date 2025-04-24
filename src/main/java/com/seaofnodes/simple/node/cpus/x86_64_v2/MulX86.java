package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class MulX86 extends MachConcreteNode implements MachNode {
    MulX86( Node mul ) { super(mul); }
    @Override public String op() { return "mul"; }
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    @Override public int twoAddress() { return 1; }
    @Override public boolean commutes() { return true; }

    @Override public void encoding( Encoding enc ) {
        // REX.W + 0F AF /r	IMUL r64, r/m64
        short dst = enc.reg(this ); // src1
        short src = enc.reg(in(2)); // src2
        enc.add1(x86_64_v2.rex(dst, src, 0));
        enc.add1(0x0F); // opcode
        enc.add1(0xAF); // opcode
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, dst, src));
    }

    // General form: "mul  dst *= src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" * ").p(code.reg(in(2)));
    }
}
