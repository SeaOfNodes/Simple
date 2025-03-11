package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.MachConcreteNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;

public class XorRISC extends MachConcreteNode implements MachNode {

    XorRISC(Node or) {
        super(or);
    }

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) {
        assert i==1 || i==2;
        return riscv.RMASK;
    }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // xor XOR R 0110011 0x4 0x00
        int beforeSize = bytes.size();


        LRG xor_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG xor_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG xor_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short self = xor_self.get_reg();
        short reg1 = xor_rg_1.get_reg();
        short reg2 = xor_rg_2.get_reg();

        int body = riscv.r_type(riscv.R_TYPE, self, 4, reg1, reg2, 0);

        riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }

    // General form
    // General form:  # # rd = rs1 ^ rs2
    @Override public void asm(CodeGen code, SB sb) {

        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" ^ ").p(code.reg(in(2)));

    }
}
