package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.MachConcreteNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;

public class AndRISC extends MachConcreteNode implements MachNode {
    AndRISC(Node and) {
        super(and);
    }

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) {
        assert i==1 || i==2;
        return com.seaofnodes.simple.node.cpus.riscv.riscv.RMASK;
    }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return com.seaofnodes.simple.node.cpus.riscv.riscv.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // and AND R 0110011 0x7 0x00
        int beforeSize = bytes.size();

        LRG and_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG and_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG and_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short self = and_self.get_reg();
        short reg1 = and_rg_1.get_reg();
        short reg2 = and_rg_2.get_reg();

        int body = com.seaofnodes.simple.node.cpus.riscv.riscv.r_type(com.seaofnodes.simple.node.cpus.riscv.riscv.R_TYPE, self, 7, reg1, reg2, 0);

        com.seaofnodes.simple.node.cpus.riscv.riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }

    // General form
    // General form:  #rd = rs1 & rs2
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" & ").p(code.reg(in(2)));
    }

    @Override public String op() { return "and"; }
}
