package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

public class CmpX86 extends MachConcreteNode implements MachNode {
    CmpX86( Node add ) { super(add); }
    @Override public String op() { return "cmp"; }
    @Override public RegMask regmap(int i) { return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.FLAGS_MASK; }
    // This one is marginal: cloning a Cmp[reg,reg] means stretching the
    // lifetimes of 2 normal registers in exchange for shortening a flags
    // register lifetime.  Spilling flags is (probably) relatively expensive
    // compared to some normal registers - and those normal registers might not
    // be spilling!
    @Override public boolean isClone() { return true; }

    @Override public void encoding( Encoding enc ) {
        short dst = enc.reg(in(1));
        short src = enc.reg(in(2));

        enc.add1(x86_64_v2.rex(dst, src, 0));
        enc.add1(0x3B);
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, dst, src));
    }
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="flags" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1))).p(", ").p(code.reg(in(2)));
    }
}
