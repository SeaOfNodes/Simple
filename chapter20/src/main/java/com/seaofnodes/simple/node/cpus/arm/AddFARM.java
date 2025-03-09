package com.seaofnodes.simple.node.cpus.arm;


import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class AddFARM extends MachConcreteNode implements MachNode {
    AddFARM(Node addf) { super(addf); }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return arm.DMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return arm.DMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // FADD(scalar) - encoding for the double-precision variant

        LRG fadd_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG fadd_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG fadd_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short self = fadd_self.get_reg();
        short reg1 = fadd_rg_1.get_reg();
        short reg2 = fadd_rg_2.get_reg();

        int beforeSize = bytes.size();

        int body = arm.f_scalar(30, 1,  reg2, 10,  reg1, self);
        riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }

    // Default on double precision for now(64 bits)
    // General form: "vadd.32  rd = src1 + src2
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" + ").p(code.reg(in(2)));
    }

    @Override public String op() { return "addf"; }
}
