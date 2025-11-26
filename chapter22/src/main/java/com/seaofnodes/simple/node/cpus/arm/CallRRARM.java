package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class CallRRARM extends CallNode implements MachNode {
    CallRRARM(CallNode call) { super(call); }
    @Override public String op() { return "callr"; }
    @Override public String label() { return op(); }
    @Override public RegMask regmap(int i) {
        return i==_inputs._len
            ? arm.RMASK                // Function call target
            : arm.callInMask(tfp(),i,fun()._maxArgSlot); // Normal argument
    }
    @Override public RegMask outregmap() { return null; }

    @Override public void encoding( Encoding enc ) {
        // Needs a register, typically a jump-and-link-register opcode
        // blr
        short self = enc.reg(this);
        enc.add4(arm.blr(arm.OP_CALLRARM, self));
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(fptr())).p("  ");
        for( int i=0; i<nargs(); i++ )
            sb.p(code.reg(arg(i))).p("  ");
        sb.unchar(2);
    }

}
