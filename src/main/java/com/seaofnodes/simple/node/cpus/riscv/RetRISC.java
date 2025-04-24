package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.ReturnNode;
import com.seaofnodes.simple.node.MachNode;

public class RetRISC extends ReturnNode implements MachNode {
    public RetRISC(ReturnNode ret, FunNode fun) { super(ret, fun); fun.setRet(this); }
    @Override public void encoding( Encoding enc ) {
        int sz = fun()._frameAdjust;
        if( sz >= 1L<<12 ) throw Utils.TODO();
        if( sz != 0 )
            enc.add4(riscv.i_type(riscv.OP_IMM, riscv.RSP, 0, riscv.RSP, sz & 0xFFF));
        short rpc = enc.reg(rpc());
        enc.add4(riscv.i_type(riscv.OP_JALR, riscv.ZERO, 0, rpc, 0));
    }
}
