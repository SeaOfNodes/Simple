package com.seaofnodes.simple.node.cpus.arm;


import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;
import java.util.BitSet;
import java.lang.StringBuilder;

// Logical Shift Right (register)
public class LsrARM extends MachConcreteNode implements MachNode {
    LsrARM(Node asr) {super(asr);}
    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) {
        // assert i==1;
        return arm.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return arm.RMASK; }

    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        LRG lsr_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG lsr_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG lsr_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short self = lsr_self.get_reg();
        short reg1 = lsr_rg_1.get_reg();
        short reg2 = lsr_rg_2.get_reg();

        int beforeSize = bytes.size();

        int body = arm.shift_reg(1238, reg2, 0x9,  reg1, self);
        arm.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }

    // General form
    // General form: "lsr rd, rs1, rs2"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" >>> ").p(code.reg(in(2)));
    }

    @Override public String op() { return "lsr"; }
}
