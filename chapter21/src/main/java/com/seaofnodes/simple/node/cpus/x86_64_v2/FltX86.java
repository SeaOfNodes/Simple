package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class FltX86 extends ConstantNode implements MachNode, RIPRelSize{
    private byte _encSize;
    FltX86(ConstantNode con ) { super(con); }
    @Override public String op() { return "ld8"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }
    @Override public boolean isClone() { return true; }
    @Override public Node copy() { return new FltX86(this); }

    @Override public void encoding( Encoding enc ) {
        // Load the constant into a FPR
        // movsd xmm, [rip + 0]
        // F2 0F 10 /r MOVSD xmm1, m64
        _encSize = 1/*Fop*/+0/*REX*/+2/*op*/+1/*MODRM*/+4/*offset*/;
        short dst = (short)(enc.reg(this ) - x86_64_v2.XMM_OFFSET);
        // Fopcode
        enc.add1(0xF2);
        // rex prefix must come next (REX.W is not set)
        _encSize += x86_64_v2.rexF(dst, 0, 0, false, enc);
        enc.add1(0x0F).add1(0x10);
        // hard-code rip here
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.INDIRECT, dst, 0x05));
        enc.add4(0);
        enc.largeConstant(this,_con,_encSize-4,2/*ELF encoding PC32*/);
    }

    // Delta is from opcode start
    @Override public byte encSize(int delta) {
        // TODO: 1-byte RIP offset?
        return (byte)_encSize;
    }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        enc.patch4(opStart+opLen-4,delta-opLen);
    }

    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" = #"));
    }
}
