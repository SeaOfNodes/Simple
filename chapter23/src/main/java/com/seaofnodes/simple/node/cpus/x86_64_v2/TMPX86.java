package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeMemPtr;
import com.seaofnodes.simple.util.SB;

public class TMPX86 extends ConstantNode implements MachNode, RIPRelSize{
    TMPX86(ConstantNode con ) { super(con); }
    @Override public String op() { return "ldp"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    @Override public boolean isClone() { return true; }
    @Override public Node copy() { return new TMPX86(this); }

    @Override public void encoding( Encoding enc ) {
        short dst = enc.reg(this);
        x86_64_v2.rexF(dst, 0, 0, true, enc);
        enc.add1(0x8D).add1(x86_64_v2.modrm(x86_64_v2.MOD.INDIRECT, dst, 0b101)).add4(0);
        int opLen = 1/*rexF*/+1/*opcode 0x8D*/+1/*modrm*/+4/*pc rel offset*/;
        // Base of constant array
        TypeMemPtr tmp = (TypeMemPtr)_con;
        int baseOff = tmp._obj.aryBase();
        enc.largeConstant(this,tmp._obj,opLen-baseOff,2/*ELF encoding PC32*/);
    }

    // Delta is from opcode start
    @Override public byte encSize(int delta) { return (byte)7; }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        enc.patch4(opStart+opLen-4,delta-opLen);
    }

    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" = #"));
    }
    @Override public boolean eq(Node n) { return this==n; }
}
