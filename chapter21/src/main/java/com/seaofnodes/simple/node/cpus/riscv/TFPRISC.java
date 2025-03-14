package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.TypeFunPtr;

public class TFPRISC extends ConstantNode implements MachNode {
    TFPRISC(ConstantNode con) { super(con); }
    @Override public String op() { return "ldx"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return riscv.WMASK; }
    @Override public boolean isClone() { return true; }
    @Override public TFPRISC copy() { return new TFPRISC(this); }
    @Override public void encoding( Encoding enc ) {
        enc.relo(this,(TypeFunPtr)_con);
        short dst = enc.reg(this);
        TypeFunPtr tfp = (TypeFunPtr)_con;
        //int body = riscv.i_type(riscv.I_TYPE, dst, 0, riscv.ZERO, (int)(ti.value() & 0xFFF));
        //enc.add4(body);
        // Surely need to split into 2 ops?
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" #"));
    }

}
