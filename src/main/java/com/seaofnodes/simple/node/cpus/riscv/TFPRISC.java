package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.isa.RiscV;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.TypeFunPtr;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;

public class TFPRISC extends ConstantNode implements MachNode, RIPRelSize {
    TFPRISC(ConstantNode con) { super(con); }
    @Override public String op() { return "ldx"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public boolean isClone() { return true; }
    @Override public TFPRISC copy() { return new TFPRISC(this); }
    @Override public void encoding( Encoding enc ) {
        enc.relo(this);
        // TODO: 1 op encoding, plus a TODO if it does not fit
        short dst = enc.reg(this);
        TypeFunPtr tfp = (TypeFunPtr)_con;
        // auipc  t0,0
        enc.add4(RiscV.u_type(RiscV.OP_AUIPC, dst, 0));
        // addi   t1,t0 + #0
        enc.add4(RiscV.i_type(RiscV.OP_IMM, dst, 0, dst, 0));
    }

    @Override public byte encSize(int delta) { return 8; }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        short rpc = enc.reg(this);
        if( opLen==8 ) {
            // ADDI sign-extends its low 12 bits, so round the AUIPC high part.
            enc.patch4(opStart  ,RiscV.u_type(RiscV.OP_AUIPC, rpc, (delta+0x800)>>12));
            enc.patch4(opStart+4,RiscV.i_type(RiscV.OP_IMM, rpc, 0, rpc, delta & 0xFFF));
        } else {
             // should not happen as one instruction is 4 byte, and TFP arm encodes 2.
            throw Utils.TODO();
        }
    }

    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" #"));
    }
    @Override public boolean eq(Node n) { return this==n; }
}
