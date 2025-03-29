package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class RetARM extends ReturnNode implements MachNode {
    RetARM(ReturnNode ret, FunNode fun) { super(ret, fun); fun.setRet(this); }
    @Override public String op() {
        return ((FunARM)fun())._frameAdjust > 0 ? "addi" : "ret";
    }
    // Correct Nodes outside the normal edges
    @Override public void postSelect(CodeGen code) {
        FunNode fun = (FunNode)rpc().in(0);
        _fun = fun;
        fun.setRet(this);
    }
    @Override public RegMask regmap(int i) { return arm.retMask(_fun.sig(),i); }
    @Override public RegMask outregmap() { return null; }
    // RET
    @Override public void encoding( Encoding enc ) {
        int frameAdjust = ((FunARM)fun())._frameAdjust;
        if( frameAdjust > 0 )
            enc.add4(arm.imm_inst(arm.OPI_ADD, (frameAdjust*-8)&0xFFF, arm.RSP, arm.RSP));
        enc.add4(arm.ret(arm.OP_RET));
    }

    @Override public void asm(CodeGen code, SB sb) {
        int frameAdjust = ((FunARM)fun())._frameAdjust;
        if( frameAdjust>0 )
            sb.p("rsp += #").p(frameAdjust*-8).p("\nret");
        // Post code-gen, just print the "ret"
        if( code._phase.ordinal() <= CodeGen.Phase.RegAlloc.ordinal() )
            // Prints return reg (either X0 or D0), RPC (always R30) and
            // then the callee-save registers.
            for( int i=2; i<nIns(); i++ )
                sb.p(code.reg(in(i))).p("  ");

        // Post-allocation, if we did not get the expected return register, print what we got
        else if( code._regAlloc.regnum(rpc()) != arm.X30 )
            sb.p("[").p(code.reg(rpc())).p("]");
    }
}
