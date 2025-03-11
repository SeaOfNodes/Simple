package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;

public class TFPARM extends ConstantNode implements MachNode {
    TFPARM( ConstantNode con ) { super(con); }
    @Override public String op() { return "ldx"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return arm.WMASK; }
    @Override public void encoding( Encoding enc ) {
        enc.funcon(this);
        short self = enc.reg(this);
        // Limit of 16bit address for function constants???
        int body = arm.load_adr(1986,0,0,self);
        enc.add4(body);
    }
    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        _con.print(sb.p(reg).p(" #"));
    }
}
