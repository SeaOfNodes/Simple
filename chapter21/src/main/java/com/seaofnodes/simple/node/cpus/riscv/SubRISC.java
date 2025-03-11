package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class SubRISC extends MachConcreteNode implements MachNode {
    SubRISC( Node sub ) { super(sub); }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return riscv.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // sub SUB R 0110011 0x0 0x20

        LRG sub_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG sub_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG sub_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short self = sub_self.get_reg();
        short reg1 = sub_rg_1.get_reg();
        short reg2 = sub_rg_2.get_reg();

        int beforeSize = bytes.size();

        int body = riscv.r_type(riscv.R_TYPE, self, 0, reg1, reg2, 0x20);

        riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;

    }

    // General form: "sub  # rd = rs1 - rs2"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" - ").p(code.reg(in(2)));
    }

    @Override public String op() { return "sub"; }
}
