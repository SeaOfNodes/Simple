package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.ReturnNode;
import com.seaofnodes.simple.node.MachNode;


public class RetARM extends ReturnNode implements MachNode {
    RetARM(ReturnNode ret, FunNode fun) { super(ret, fun); fun.setRet(this); }
    @Override public String op() { return "ret"; }
    @Override public RegMask regmap(int i) { return arm.retMask(_fun.sig(),i); }
    @Override public RegMask outregmap() { return null; }
    @Override public void encoding( Encoding enc ) {
        enc.add4(arm.ret(3512256));
    }

    @Override public void asm(CodeGen code, SB sb) {
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

    // Correct Nodes outside the normal edges
    @Override public void postSelect() {
        FunNode fun = (FunNode)rpc().in(0);
        _fun = fun;
        fun.setRet(this);
    }
}
