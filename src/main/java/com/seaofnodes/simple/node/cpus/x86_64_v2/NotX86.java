package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

public class NotX86 extends MachConcreteNode implements MachNode {
    NotX86(NotNode not) { super(not); }
    @Override public String op() { return "not"; }
    @Override public RegMask regmap(int i) { return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.RMASK;  }
    @Override public RegMask killmap() { return x86_64_v2.FLAGS_MASK; }

    @Override public void encoding( Encoding enc ) {
        assert !(in(1) instanceof  NotNode); // Cleared out by peeps
        assert !(in(1) instanceof BoolNode); // Cleared out by peeps
        short dst = enc.reg(this );
        short src = enc.reg(in(1));

        // test   rdi,rdi
        enc.add1(x86_64_v2.rex(src, src, 0));
        enc.add1(0x85);
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, src, src));

        // setz dstb; then zero-extend the low byte into the full register.
        if( dst >= 4 ) enc.add1(x86_64_v2.rex(0, dst, 0, false));
        enc.add1(0x0F);
        enc.add1(0x94);
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0, dst));
        if( dst >= 4 ) enc.add1(x86_64_v2.rex(dst, dst, 0, false));
        enc.add1(0x0F);
        enc.add1(0xB6);
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, dst, dst));
    }
    @Override public void asm(CodeGen code, SB sb) { sb.p(code.reg(this)); }
}
