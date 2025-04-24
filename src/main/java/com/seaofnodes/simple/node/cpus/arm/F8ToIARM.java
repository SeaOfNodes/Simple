package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.isa.Arm64;
import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class F8ToIARM extends MachConcreteNode implements MachNode {
    F8ToIARM(Node f8toi) { super(f8toi); }
    @Override public String op() { return "cvti"; }
    @Override public RegMask regmap(int i) { return arm.DMASK; }
    @Override public RegMask outregmap() { return arm.WMASK; }
    @Override public void encoding( Encoding enc ) {
        enc.add4(Arm64.floatToInteger(enc.reg(in(1))-arm.D_OFFSET,enc.reg(this)));
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p("(int)").p(code.reg(in(1)));
    }

}
