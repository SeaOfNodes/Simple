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
        enc.relo(this);
        // TODO: 1 op encoding, plus a TODO if it does not fit
        short dst = enc.reg(this);
        TypeFunPtr tfp = (TypeFunPtr)_con;
        // auipc  t0,0
        int auipc = riscv.u_type(0b0010111, dst, 0);
        // addi   t1,t0 + #0
        int addi = riscv.i_type(0b0010011, dst, 0, dst, 0);
        enc.add4(auipc);
        enc.add4(addi);
    }

    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" #"));
    }

}
