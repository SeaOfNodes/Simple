package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.ReturnNode;
import com.seaofnodes.simple.node.MachNode;

public class RetRISC extends ReturnNode implements MachNode{
    RetRISC(ReturnNode ret, FunNode fun) { super(ret, fun); fun.setRet(this); }
    @Override public String op() { return "ret"; }
    // Correct Nodes outside the normal edges
    @Override public void postSelect(CodeGen code) {
        FunNode fun = (FunNode)rpc().in(0);
        _fun = fun;
        fun.setRet(this);
    }
    @Override public RegMask regmap(int i) { return riscv.retMask(_fun.sig(),i); }
    @Override public RegMask outregmap() { return null; }
    @Override public void encoding( Encoding enc ) {
        short rpc = enc.reg(rpc());
        int body = riscv.i_type(0x67, riscv.ZERO, 0, rpc, 0);
        enc.add4(body);
    }

    @Override public void asm(CodeGen code, SB sb) {
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
