package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.MachConcreteNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;

import java.io.ByteArrayOutputStream;

// Not using the ORRS variant.
public class OrARM extends MachConcreteNode implements MachNode {
    OrARM(Node and) {
        super(and);
    }

    // Register mask allowed on input i.
    // This is the normal calling convention
    @Override public RegMask regmap(int i) {
        assert i==1 || i==2;
        return arm.RMASK;
    }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return arm.RMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        LRG or_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG or_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        LRG or_rg_2 = CodeGen.CODE._regAlloc.lrg(in(2));

        short self = or_self.get_reg();
        short reg1 = or_rg_1.get_reg();
        short reg2 = or_rg_2.get_reg();

        int beforeSize = bytes.size();

        int body = arm.r_reg(170, 0, reg2, 0,  reg1, self);
        arm.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }

    // General form
    // General form:  #rd = rs1 & rs2
    @Override public void asm(CodeGen code, SB sb) {

        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1))).p(" | ").p(code.reg(in(2)));
    }

    @Override public String op() { return "orr"; }
}
