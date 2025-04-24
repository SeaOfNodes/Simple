package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// Logical Shift Right (register)
public class LsrARM extends MachConcreteNode implements MachNode {
    LsrARM(Node asr) {super(asr);}
    @Override public String op() { return "shr"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.WMASK; }

    @Override public void encoding( Encoding enc ) { arm.shift_reg(enc,this,arm.OP_LSR); }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" >>> ").p(code.reg(in(2)));
    }

}
