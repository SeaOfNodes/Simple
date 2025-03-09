package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.SplitNode;
import com.seaofnodes.simple.node.cpus.riscv.riscv;

import java.io.ByteArrayOutputStream;

public class SplitARM extends SplitNode {
    SplitARM(String kind, byte round) { super(kind,round,new Node[2]);}

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { return arm.SPLIT_MASK; }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return arm.SPLIT_MASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // mov(register)
        LRG split_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG split_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));

        short self_reg = split_self.get_reg();
        short reg_1 = split_rg_1.get_reg();

        int beforeSize = bytes.size();

        int body = arm.r_reg(170, 0, reg_1, 0, 31, self_reg);
        riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }
}
