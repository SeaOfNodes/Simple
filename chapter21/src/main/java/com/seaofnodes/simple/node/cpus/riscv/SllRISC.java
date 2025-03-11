package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// Shift left logical
public class SllRISC extends MachConcreteNode implements MachNode{
    SllRISC(Node sll) {super(sll);}

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) {
        //assert i==1;
        return riscv.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // Shift Left Logical R 0110011 0x1
        int beforeSize = bytes.size();

        LRG sll_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG sll_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG sll_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short self = sll_self.get_reg();
        short reg1 = sll_rg_1.get_reg();
        short reg2 = sll_rg_2.get_reg();

        int body = riscv.r_type(riscv.R_TYPE, self, 1, reg1, reg2, 0);

        riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }

    // General form
    // General form: "sll rd, rs1, rs2"
    @Override public void asm(CodeGen code, SB sb) {

        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" << ").p(code.reg(in(2)));

    }

    @Override public String op() { return "sll"; }
}
