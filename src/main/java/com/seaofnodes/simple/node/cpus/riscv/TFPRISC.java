package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.TypeFunPtr;

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
        int auipc = riscv.u_type(riscv.OP_AUIPC, dst, 0);
        // addi   t1,t0 + #0
        int addi = riscv.i_type(riscv.OP_IMM, dst, 0, dst, 0);
        enc.add4(auipc);
        enc.add4(addi);
    }

    @Override public byte encSize(int delta) {
        if( -(1L<<11) <= delta && delta < (1L<<11) ) return 4;
        throw Utils.TODO();
    }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        short rpc = enc.reg(this);
        if( opLen==8 ) {
            // AUIPC (upper 20 bits)
            // opstart of add
            int next = opStart + opLen;
            enc.patch4(opStart,riscv.u_type(riscv.OP_AUIPC, rpc, delta));
            // addi(low 12 bits)
            enc.patch4(next,riscv.i_type(riscv.OP_IMM, rpc, 0, rpc, delta & 0xFFF));
            // addi
        } else {
             // should not happen as one instruction is 4 byte, and TFP arm encodes 2.
            throw Utils.TODO();
        }
    }

    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" #"));
    }

}
