package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class RetARM extends ReturnNode implements MachNode {
    RetARM(ReturnNode ret, FunNode fun) { super(ret, fun); fun.setRet(this); }
    @Override public void encoding( Encoding enc ) {
        int frameAdjust = ((FunARM)fun())._frameAdjust;
        if( frameAdjust > 0 )
            enc.add4(arm.imm_inst(arm.OPI_ADD, (frameAdjust*-8)&0xFFF, arm.RSP, arm.RSP));
        enc.add4(arm.ret(arm.OP_RET));
    }
}
