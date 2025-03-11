package com.seaofnodes.simple.node.cpus.riscv;


import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

import com.seaofnodes.simple.node.MachConcreteNode;

public class NotRISC extends MachConcreteNode implements MachNode {
    NotRISC(NotNode not) {super(not);}
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.RMASK;  }

    @Override public int twoAddress( ) { return 0; }

    @Override public void encoding( Encoding enc ) {
        //seqz rd, rs
        // sltiu rd, rs, 1
        int beforeSize = bytes.size();

        LRG not_rg = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG out_rg = CodeGen.CODE._regAlloc.lrg(this);
        short reg1 = not_rg.get_reg();
        short out_reg = out_rg.get_reg();

        // sltiu rd, rs, 1
        int body = riscv.i_type(19, out_reg, 0x3, reg1, 1);

        riscv.push_4_bytes(body, bytes);
        return bytes.size() - beforeSize;
    }

    @Override public void asm(CodeGen code, SB sb) { sb.p(code.reg(this)); }
    @Override public String op() { return "not"; }
}
