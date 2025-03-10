package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.riscv.riscv;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

import com.seaofnodes.simple.node.MachConcreteNode;

public class NotARM extends MachConcreteNode implements MachNode{
    NotARM(NotNode not) {super(not);}
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.RMASK;  }

    @Override public int twoAddress( ) { return 0; }

    @Override public int encoding(ByteArrayOutputStream bytes) {
        // subs xzr, rs, #0
        // cset    rd, eq        // Set rd to 1 if rs == 0 (equal), else 0
        // subtracting zero from rs will just yield rs, it sets the zero flag and then it's used in cset

        LRG not_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG not_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));

        short self = not_self.get_reg();
        short reg1 = not_rg_1.get_reg();

        int beforeSize = bytes.size();

        int body_subs = arm.imm_inst(964, 0, reg1, reg1);
        int body_cset = arm.cond_set(1236, 31, arm.COND.EQ, 63, reg1);

        arm.push_4_bytes(body_subs, bytes);
        arm.push_4_bytes(body_cset, bytes);

        return bytes.size() - beforeSize;
    }

    @Override public void asm(CodeGen code, SB sb) { sb.p(code.reg(this)); }
    @Override public String op() { return "not"; }
}
