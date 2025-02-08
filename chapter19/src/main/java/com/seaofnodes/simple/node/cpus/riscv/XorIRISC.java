package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.CodeGen;
import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.MachConcreteNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.cpus.x86_64_v2.x86_64_v2;
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
    @Override public RegMask outregmap() { return riscv.RMASK; }

    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // General form
    // General form: "rd = rs1 ^ imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" ^ #");
        _ti.print(sb);
    }

    @Override public String op() { return "xori"; }
}
