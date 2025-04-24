package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class DivX86 extends MachConcreteNode implements MachNode {
    DivX86( Node div ) { super(div); }
    @Override public String op() { return "div"; }
    @Override public RegMask regmap(int i) {
        return (i==1) ? x86_64_v2.RAX_MASK : x86_64_v2.RMASK;
    }
    @Override public RegMask outregmap() { return x86_64_v2.RAX_MASK; }
    @Override public RegMask killmap() { return x86_64_v2.RDX_MASK; }

    @Override public void encoding( Encoding enc ) {
        // REX.W + F7 /7	IDIV r/m64
        short src = enc.reg(in(2));
        // sign extend rax before 128/64 bits division
        // sign extend rax to rdx:rax
        enc.add1(x86_64_v2.REX_W);
        enc.add1(0x99);
        enc.add1(x86_64_v2.rex(0, src, 0));
        // divide
        enc.add1(0xF7); // opcode
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0x07, src));
    }
    // General form: "div  dst /= src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" / ").p(code.reg(in(2)));
    }
    @Override public String comment() { return "kill rdx"; }
}
