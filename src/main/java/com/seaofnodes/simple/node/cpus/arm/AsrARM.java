package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// Arithmetic Shift Right (register)
public class AsrARM extends MachConcreteNode implements MachNode {
    AsrARM(Node asr) {super(asr);}
    @Override public String op() { return "sar"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.DMASK; }
    @Override public void encoding( Encoding enc ) { arm.shift_reg(enc,this,arm.OP_ASR); }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" >> ").p(code.reg(in(2)));
    }

}
