package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;


// mulh signed multiply instruction(no-imm form)
public class MulRISC extends MachConcreteNode implements MachNode{
    MulRISC(Node mul) {super(mul);}

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return riscv.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // mul MUL R 0110011 0x0 0x01
        int beforeSize = bytes.size();

        LRG self  = CodeGen.CODE._regAlloc.lrg(this);
        LRG mul_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG mul_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short reg1 = mul_rg_1.get_reg();
        short reg2 = mul_rg_2.get_reg();
        short reg_self = mul_rg_2.get_reg();

        int body = riscv.r_type(riscv.R_TYPE, reg_self,0,  reg1, reg2, 1);

        riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }

    // General form: "rd = rs1 * rs2"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" * ").p(code.reg(in(2)));
    }

    @Override public String op() { return "mul"; }

}
