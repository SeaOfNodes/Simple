package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class DivRISC extends MachConcreteNode implements MachNode{
    DivRISC(Node div) {super(div);}

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) {
        assert i==1 || i==2;
        return riscv.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // div DIV R 0110011 0x4 0x01
        LRG self  = CodeGen.CODE._regAlloc.lrg(this);
        LRG div_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG div_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short reg1 = div_rg_1.get_reg();
        short reg2 = div_rg_2.get_reg();
        short reg_self = self.get_reg();

        int beforeSize = bytes.size();
        int body = riscv.r_type(riscv.R_TYPE, reg_self,4,  reg1, reg2, 1);

        riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }

    // Signed division
    // General form: "rd  = rs1 / rs"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" / ").p(code.reg(in(2)));
    }

    @Override public String op() { return "div"; }
}
