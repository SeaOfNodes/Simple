package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

import com.seaofnodes.simple.node.MachConcreteNode;

public class NotARM extends MachConcreteNode implements MachNode{
    NotARM(NotNode not) {super(not);}
    @Override public String op() { return "not"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.WMASK;  }
    @Override public RegMask killmap() { return arm.FLAGS_MASK; }
    @Override public void encoding( Encoding enc ) {
        // subs xzr, rs, #0
        // cset    rd, eq        // Set rd to 1 if rs == 0 (equal), else 0
        // subtracting zero from rs will just yield rs, it sets the zero flag and then it's used in cset
        short self = enc.reg(this );
        short reg1 = enc.reg(in(1));
        int subs = arm.imm_inst(arm.OP_SUBS, 0, reg1, self);
        enc.add4(subs);
        int cset = arm.cond_set(arm.OP_CSET, 31, arm.COND.EQ, 63, reg1);
        enc.add4(cset);
    }

    @Override public void asm(CodeGen code, SB sb) { sb.p(code.reg(this)); }
}
