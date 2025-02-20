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


public class XorX86 extends MachConcreteNode implements MachNode{
    XorX86(Node xor) {super(xor);}

    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.WMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }

    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // REX.W + 33 /r	XOR r64, r/m64

        LRG xor_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG xor_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short reg1 = xor_rg_1.get_reg();
        short reg2 = xor_rg_2.get_reg();

        int beforeSize = bytes.size();

        bytes.write(x86_64_v2.rex(reg1, reg2, 0));

        bytes.write(0x33); // opcode

        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, reg1, reg2));

        return bytes.size() - beforeSize;
    }

    // General form
    // General form: "xor ^ reg
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" ^ ").p(code.reg(in(2)));
    }

    @Override public String op() { return "xor"; }

}
