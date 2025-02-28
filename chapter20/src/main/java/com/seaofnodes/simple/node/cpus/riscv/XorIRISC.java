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

public class XorIRISC extends MachConcreteNode implements MachNode{
    final TypeInteger _ti;
    XorIRISC(Node xor, TypeInteger ti) {super(xor); _inputs.pop(); _ti = ti;}

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) {
        assert i==1 || i==2;

        return riscv.RMASK; }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // XOR Immediate I 0010011 0x4
        LRG xori_rg = CodeGen.CODE._regAlloc.lrg(this);
        LRG in_rg = CodeGen.CODE._regAlloc.lrg(in(1));

        short rd = xori_rg.get_reg();
        short in_reg = in_rg.get_reg();

        int beforeSize = bytes.size();

        int imm32_8 = (int)_ti.value();

        int body = riscv.i_type(riscv.I_TYPE, rd, 4, in_reg, imm32_8, 0);
        riscv.push_4_bytes(body, bytes);
        return bytes.size() - beforeSize;
    }

    // General form
    // General form: "rd = rs1 ^ imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" ^ #");
        _ti.print(sb);
    }

    @Override public String op() { return "xori"; }
}
