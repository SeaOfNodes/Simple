package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
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
        _encSize=X86.rip(enc,0xF2,0x0F10,false,enc.reg(this)-x86_64_v2.XMM_OFFSET);
        enc.largeConstant(this,_con,_encSize-4,2/*ELF PC32*/);
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
