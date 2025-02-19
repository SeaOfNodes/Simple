package com.seaofnodes.simple.node.cpus.x86_64_v2;

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

public class XorIX86  extends MachConcreteNode implements MachNode {

    final TypeInteger _ti;
    XorIX86(Node xor, TypeInteger ti) {super(xor); _inputs.pop(); _ti = ti;}

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.WMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }


    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // REX.W + 83 /6 ib	XOR r/m64, imm8
        LRG xor_rg_1 = CodeGen.CODE._regAlloc.lrg(this);

        short reg = xor_rg_1.get_reg();
        int beforeSize = bytes.size();

        bytes.write(x86_64_v2.rex(0, reg));

        bytes.write(0x83); // opcode

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0x06, reg));

        // immediate(4 bytes) 32 bits
        int imm8 = (int)_ti.value();
        x86_64_v2.imm(imm8, 8, bytes);
        return bytes.size() - beforeSize;
    }

    // General form
    // General form: "ori  xori ^ #imm"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" ^ #");
        _ti.print(sb);
    }

    @Override public String op() { return "xori"; }
}
