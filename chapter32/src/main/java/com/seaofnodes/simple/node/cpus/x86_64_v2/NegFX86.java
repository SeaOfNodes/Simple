package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFloat;
import com.seaofnodes.simple.util.SB;

// Flip the sign bit, preserving signed zero, infinities, and NaN payloads.
public class NegFX86 extends MachConcreteNode implements MachNode {
    NegFX86(MinusNode neg) {
        super(neg);
        addDef(new FltX86(new ConstantNode(TypeFloat.constant(-0.0)),null));
    }
    @Override public String op() { return "negf"; }
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return x86_64_v2.XMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }
    @Override public int twoAddress() { return 1; }
    @Override public void encoding(Encoding enc) {
        X86.sse(enc,0x66,0x0F57,false,enc.reg(this)-x86_64_v2.XMM_OFFSET,enc.reg(in(2))-x86_64_v2.XMM_OFFSET);
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" ^= ").p(code.reg(in(2))).p(" // sign bit");
    }
}
