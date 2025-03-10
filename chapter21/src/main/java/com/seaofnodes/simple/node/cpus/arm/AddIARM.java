package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class AddIARM extends MachConcreteNode implements MachNode {
    final TypeInteger _ti;

    AddIARM(Node add, TypeInteger ti) {
        super(add);
        _inputs.pop();
        _ti = ti;
    }

    // Register mask allowed on input i.
    @Override
    public RegMask regmap(int i) {
        // assert i== i;
        return arm.RMASK;
    }

    // Register mask allowed as a result.  0 for no register.
    @Override
    public RegMask outregmap() {
        return arm.RMASK;
    }

    // Encoding is appended into the byte array; size is returned
    @Override
    public int encoding(ByteArrayOutputStream bytes) {
        // Todo: how to handle more than 12 bits
        // Only unsigned - need specific op for minus imm

        LRG rg_1 = CodeGen.CODE._regAlloc.lrg(this);
        LRG rg_2 = CodeGen.CODE._regAlloc.lrg(in(1));

        int beforeSize = bytes.size();

        short rd = rg_1.get_reg();
        short reg_1 = rg_2.get_reg();

        int imm = (int)_ti.value();

        int body = arm.imm_inst(580, imm, reg_1, rd);

        arm.push_4_bytes(body, bytes);
        return bytes.size() - beforeSize;
    }

    // General form: "addi  rd = rs1 + imm"
    @Override
    public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" + #");
        _ti.print(sb);
    }

    @Override
    public String op() {
        return _ti.value() == 1 ? "inc" : (_ti.value() == -1 ? "dec" : "addi");
    }

}