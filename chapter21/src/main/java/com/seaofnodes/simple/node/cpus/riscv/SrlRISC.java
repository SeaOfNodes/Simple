package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// Right Shift Logical
public class SrlRISC extends MachConcreteNode implements MachNode {
    SrlRISC(Node srli) {super(srli);}

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) {
        // assert i==1;
        return riscv.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // srl Shift Right Logical R 0110011 0x5 0x00
        int beforeSize = bytes.size();

        LRG srl_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG srl_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG srl_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short self = srl_self.get_reg();
        short reg1 = srl_rg_1.get_reg();
        short reg2 = srl_rg_2.get_reg();

        int body = riscv.r_type(riscv.R_TYPE, self, 7, reg1, reg2, 0);

        riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }

    // General form
    // General form: "srl rd, rs1, rs2"
    @Override public void asm(CodeGen code, SB sb) {

        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" >>> ").p(code.reg(in(2)));
    }

    @Override public String op() { return "srl"; }
}
