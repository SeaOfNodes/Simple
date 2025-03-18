package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.TypeFunPtr;

public class TFPARM extends ConstantNode implements MachNode {
    TFPARM( ConstantNode con ) { super(con); }
    @Override public String op() { return "ldx"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return arm.WMASK; }
    @Override public boolean isClone() { return true; }
    @Override public TFPARM copy() { return new TFPARM(this); }
    @Override public void encoding( Encoding enc ) {
        enc.relo(this);
        short self = enc.reg(this);
        // adrp    x0, 0
        int adrp = arm.adrp(1,0, 0b10000, 0,self);
        // add     x0, x0, 0
        arm.imm_inst(enc,this,0b1001000100,0);
        enc.add4(adrp);
    }
    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        _con.print(sb.p(reg).p(" #"));
    }
}
