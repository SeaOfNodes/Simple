package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.TypeFunPtr;

// Add upper 20bits to PC.  Immediate comes from the relocation info.
public class AUIPC extends ConstantNode implements MachNode {
    AUIPC( TypeFunPtr tfp ) { super(tfp); }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public boolean isClone() { return true; }
    @Override public AUIPC copy() { return new AUIPC((TypeFunPtr)_con); }
    @Override public String op() { return "auipc"; }
    @Override public void encoding( Encoding enc ) {
        enc.relo(this,(TypeFunPtr)_con);
        short dst = enc.reg(this);
        enc.add4(riscv.u_type(0x17, dst, 0));
    }

    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        sb.p(reg).p(" = PC+#");
        if( _con == null ) sb.p("---");
        else _con.print(sb);
    }
}
