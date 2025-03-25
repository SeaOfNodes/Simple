package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.ReturnNode;
import com.seaofnodes.simple.node.MachNode;

public class RetRISC extends ReturnNode implements MachNode {
    public RetRISC(ReturnNode ret, FunNode fun) {
        super(ret, fun);
        if( fun != null ) fun.setRet(this);
    }
    @Override public String op() {
        return fun()._frameAdjust > 0 ? "addi" : "ret";
    }
    // Correct Nodes outside the normal edges
    @Override public void postSelect(CodeGen code) {
        FunNode fun = (FunNode)rpc().in(0);
        _fun = fun;
        fun.setRet(this);
    }
    @Override public RegMask regmap(int i) { return riscv.retMask(_fun.sig(),i); }
    @Override public RegMask outregmap() { return null; }
    @Override public void encoding( Encoding enc ) {
        int frameAdjust = fun()._frameAdjust;
        if( frameAdjust > 0 )
            enc.add4(riscv.i_type(riscv.I_TYPE, riscv.SP, 0, riscv.SP, (frameAdjust*-8) & 0xFFF));
        short rpc = enc.reg(rpc());
        enc.add4(riscv.i_type(0x67, riscv.ZERO, 0, rpc, 0));
    }

    @Override public void asm(CodeGen code, SB sb) {
        int frameAdjust = fun()._frameAdjust;
        if( frameAdjust>0 )
            sb.p("rsp += #").p(frameAdjust*-8).p("\nret");
        // Post code-gen, just print the "ret"
        if( code._phase.ordinal() <= CodeGen.Phase.RegAlloc.ordinal() )
            // Prints return reg (either A0 or FA0), RPC (always R1) and then
            // the callee-save registers.
            for( int i=2; i<nIns(); i++ )
                sb.p(code.reg(in(i))).p("  ");

        // Post-allocation, if we did not get the expected return register, print what we got
        else if( code._regAlloc.regnum(rpc()) != riscv.RPC )
            sb.p("[").p(code.reg(rpc())).p("]");
    }
}
