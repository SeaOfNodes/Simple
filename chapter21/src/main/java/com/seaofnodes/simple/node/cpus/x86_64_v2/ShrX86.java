package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.Node;

public class ShrX86 extends RegX86 {
    ShrX86( Node add ) { super(add); }
    @Override public String op() { return "shr"; }
    @Override public String glabel() { return ">>>"; }
    @Override public RegMask regmap(int i) {
        if (i == 1) return x86_64_v2.WMASK;
        if (i == 2) return x86_64_v2.RCX_MASK;
        throw Utils.TODO();
    }
    @Override int opcode() { return 0xD3; }
    @Override public final void encoding( Encoding enc ) {
        short dst = enc.reg(this ); // src1
        short src = enc.reg(in(2)); // src2
        enc.add1(x86_64_v2.rex(0, dst, 0));
        enc.add1(opcode()); // opcode
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 5, dst));
    }
}
