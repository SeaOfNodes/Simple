package com.seaofnodes.simple.node.cpus.arm;

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

public class XorARM extends MachConcreteNode implements MachNode{
    XorARM(Node xor) {super(xor);}

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) {
        assert i==1 || i==2;

        return arm.RMASK; }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return arm.RMASK; }

    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        LRG xor_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG xor_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG xor_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short self = xor_self.get_reg();
        short reg1 = xor_rg_1.get_reg();
        short reg2 = xor_rg_2.get_reg();

        int beforeSize = bytes.size();

        int body = arm.r_reg(202, 0, reg2, 0,  reg1, self);
        arm.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }

    // General form
    // General form: "rd = x1 ^ x2"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" ^ ").p(code.reg(in(2)));
    }

    @Override public String op() { return "eor"; }
}