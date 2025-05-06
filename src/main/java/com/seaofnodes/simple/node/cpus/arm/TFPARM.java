package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.TypeFunPtr;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;

public class TFPARM extends ConstantNode implements MachNode, RIPRelSize {
    TFPARM( ConstantNode con ) { super(con); }
    @Override public String op() { return "ldx"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return arm.WMASK; }
    @Override public boolean isClone() { return true; }
    @Override public TFPARM copy() { return new TFPARM(this); }
    @Override public void encoding( Encoding enc ) {
        enc.relo(this);
        short self = enc.reg(this);
        // adrp    x0, 0
        int adrp = arm.adrp(1,0, arm.OP_ADRP, 0,self);
        // add     x0, x0, 0
        enc.add4(adrp);
        arm.imm_inst(enc,arm.OPI_ADD,0, 0);
    }

    @Override public byte encSize(int delta) {
        return 8;
    }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        short rpc = enc.reg(this);
        if(opLen == 8 ) {
            // ARM encoding delta is from PC & 0xFFF
            int target = opStart+delta;
            int base = opStart & ~0xFFF;
            delta = target-base;
            int adrp_delta = delta >> 12;
            // patch upper 20 bits via adrp
            enc.patch4(opStart, arm.adrp(1, adrp_delta & 0b11, 0b10000, adrp_delta >> 2, rpc));
            // low 12 bits via add
            enc.patch4(opStart+4, arm.imm_inst_l(arm.OPI_ADD, delta & 0xfff, rpc));
        } else {
            // should not happen as one instruction is 4 byte, and TFP arm encodes 2.
            throw Utils.TODO();
        }
    }

    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        _con.print(sb.p(reg).p(" #"));
    }
    @Override public boolean eq(Node n) { return this==n; }
}
