package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class CallRRISC extends CallNode implements MachNode {
    CallRRISC( CallNode call ) { super(call); }
    @Override public String op() { return "callr"; }
    @Override public String label() { return op(); }
    @Override public RegMask regmap(int i) {
        return i==_inputs._len-1
            ? riscv.RMASK                // Function call target
            : riscv.callInMask(tfp(),i,fun()._maxArgSlot); // Normal argument
    }
    @Override public RegMask outregmap() { return null; }

    @Override public void encoding( Encoding enc ) {
        short rpc = enc.reg(this);
        short src = enc.reg(in(_inputs._len-1));
        int body = riscv.i_type(riscv.OP_JALR, rpc, 0, src, 0);
        enc.add4(body);
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(fptr())).p("  ");
        for( int i=0; i<nargs(); i++ )
            sb.p(code.reg(arg(i))).p("  ");
        sb.unchar(2);
    }
}
