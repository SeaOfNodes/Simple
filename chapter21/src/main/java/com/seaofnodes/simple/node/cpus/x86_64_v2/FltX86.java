package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class FltX86 extends ConstantNode implements MachNode {
    FltX86(ConstantNode con ) { super(con); }
    @Override public String op() { return "fld"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }
    @Override public boolean isClone() { return true; }
    @Override public Node copy() { return new FltX86(this); }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        enc.largeConstant(this,_con);
        // Simply move the constant into a FPR
        // movsd xmm, [rip + 0]
        // F2 0F 10 /r MOVSD xmm1, m64
        short dst = (short)(enc.reg(this ) - x86_64_v2.XMM_OFFSET);
        enc.add1(x86_64_v2.rex(dst, 0, 0));

        // Fopcode
        enc.add1(0xF2);
        enc.add1(0x0F);
        enc.add1(0x10);

        // hard-code rip here
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.INDIRECT, dst, 0x05));
        enc.add4(0);
    }

    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" #"));
    }
}
