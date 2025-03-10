package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;


// mulh signed multiply instruction(no-imm form)
public class MulARM extends MachConcreteNode implements MachNode{
    MulARM(Node mul) {super(mul);}

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return arm.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return arm.RMASK; }
    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        LRG mul_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG mul_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG mul_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short self = mul_self.get_reg();
        short reg1 = mul_rg_1.get_reg();
        short reg2 = mul_rg_2.get_reg();

        int beforeSize = bytes.size();

        int body = arm.madd(1240,  reg2,31, reg1, self);

        arm.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;

    }

    // General form: "rd = rs1 * rs2"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" * ").p(code.reg(in(2)));
    }

    @Override public String op() { return "mul"; }

}
