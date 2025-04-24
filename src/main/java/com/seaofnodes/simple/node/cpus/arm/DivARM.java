package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class DivARM extends MachConcreteNode implements MachNode {
    DivARM( Node div ) { super(div); }
    @Override public String op() { return "div"; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.WMASK; }
    // SDIV
    @Override public void encoding( Encoding enc ) { arm.madd(enc,this,arm.OP_DIV,3); }
    // General form: "div  dst /= src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" / ").p(code.reg(in(2)));
    }

}
