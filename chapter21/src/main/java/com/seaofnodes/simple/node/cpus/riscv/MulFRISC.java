package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// fmul.s
public class MulFRISC extends MachConcreteNode implements MachNode{
    MulFRISC(Node mulf) {super(mulf);}

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return riscv.FMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.FMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // fmul.d
        LRG fmul_self = CodeGen.CODE._regAlloc.lrg(this);

        LRG fmul_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG fmul_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        int beforeSize = bytes.size();

        short reg1 = fmul_rg_1.get_reg();
        short rd = fmul_self.get_reg();
        short reg2 = fmul_rg_2.get_reg();


        int body = riscv.r_type(0x53, rd - riscv.F_OFFSET, riscv.RM.RNE, reg1 - riscv.F_OFFSET, reg2 - riscv.F_OFFSET, 0x9);

        riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;

    }

    // Default on double precision for now(64 bits)
    // General form: "fmul.d  rd = src1 * src2
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" * ").p(code.reg(in(2)));
    }

    @Override public String op() { return "mulf"; }

}
