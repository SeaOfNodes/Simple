package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.MachConcreteNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

// Right Shift Arithmetic
public class SraRISC extends MachConcreteNode implements MachNode {

    SraRISC(Node sra) {super(sra);}

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) {
        // assert i==1;
        return riscv.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // sra Shift Right Arith* R 0110011 0x5

        int beforeSize = bytes.size();

        LRG sra_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG sra_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG sra_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short self = sra_self.get_reg();
        short reg1 = sra_rg_1.get_reg();
        short reg2 = sra_rg_2.get_reg();

        int body = riscv.r_type(riscv.R_TYPE, self, 5, reg1, reg2, 0x20);

        riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }

    // General form
    // General form: "sra rd, rs1, rs2"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" >> ").p(code.reg(in(2)));
    }

    @Override public String op() { return "sra"; }
}
