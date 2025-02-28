package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.x86_64_v2.x86_64_v2;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;
import java.util.BitSet;

// Shift left Logical immediate
public class SllIRISC extends MachConcreteNode implements MachNode{
    final TypeInteger _ti;
    SllIRISC(Node sll, TypeInteger ti) {super(sll); _inputs.pop(); _ti = ti;}

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) {
        //assert i==1;
        return riscv.RMASK; }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // slli Shift Left Logical Imm I 0010011 0x1
        LRG slli_rd = CodeGen.CODE._regAlloc.lrg(this);
        LRG slli_in = CodeGen.CODE._regAlloc.lrg(in(1));

        short slli_rd_reg = slli_rd.get_reg();
        short slli_in_reg = slli_in.get_reg();

        int beforeSize = bytes.size();

        int imm32_8 = (int)_ti.value();
        int body = riscv.i_type(riscv.I_TYPE, slli_rd_reg, 1, slli_in_reg, imm32_8, 0);

        riscv.push_4_bytes(body, bytes);


        return bytes.size() - beforeSize;
    }

    // General form
    // General form: "slli rd, rs1, imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" << #");
        _ti.print(sb);
    }

    @Override public String op() { return "slli"; }
}
