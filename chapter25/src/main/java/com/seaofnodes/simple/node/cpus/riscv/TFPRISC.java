package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFunPtr;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;

public class TFPRISC extends FunPtrNode implements MachNode, RIPRelSize {
    final String _ext;
    TFPRISC( FunPtrNode fptr, String ext ) { super(fptr); _ext = ext; }
    @Override public String op() { return "ldx"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public boolean isClone() { return true; }
    @Override public TFPRISC copy() { return new TFPRISC(this,_ext); }
    @Override public void encoding( Encoding enc ) {
        if( _ext!=null ) throw Utils.TODO();
        enc.relo(this);
        // TODO: 1 op encoding, plus a TODO if it does not fit
        short dst = enc.reg(this);
        // auipc  t0,0
        enc.add4(riscv.u_type(riscv.OP_AUIPC, dst, 0));
        // addi   t1,t0 + #0
        enc.add4(riscv.i_type(riscv.OP_IMM, dst, 0, dst, 0));
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
            enc.patch4(opStart,riscv.u_type(riscv.OP_AUIPC, rpc, delta>>12));
            // addi(low 12 bits)
            enc.patch4(next,riscv.i_type(riscv.OP_IMM, rpc, 0, rpc, delta & 0xFFF));
            // addi
        } else {
             // should not happen as one instruction is 4 byte, and TFP arm encodes 2.
            throw Utils.TODO();
        }
    }

    @Override public void asm(CodeGen code, SB sb) {
        _type.print(sb.p(code.reg(this)).p(" #"));
    }
    @Override public boolean eq(Node n) { return this==n; }
}
