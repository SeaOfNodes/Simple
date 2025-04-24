package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class AddRISC extends MachConcreteNode implements MachNode {
    public AddRISC( Node add ) { super(add); }
    AddRISC( Node base, Node off ) {
        super(new Node[3]);
        _inputs.set(1,base);
        _inputs.set(2,off );
    }
    @Override public String op() { return "add"; }
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public void encoding( Encoding enc ) { riscv.r_type(enc,this,0,0);  }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" + ").p(code.reg(in(2)));
    }
}
