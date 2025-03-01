package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.SplitNode;
import java.io.ByteArrayOutputStream;

public class SplitRISC extends SplitNode {
    SplitRISC( String kind, byte round ) { super(kind,round, new Node[2]); }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { return riscv.MEM_MASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.MEM_MASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // add rd, x0, rs2
        LRG split_self = CodeGen.CODE._regAlloc.lrg(this);
        LRG split_rg_1 = CodeGen.CODE._regAlloc.lrg(in(1));
        // second reg is zero

        short self_reg = split_self.get_reg();
        short reg_1 = split_rg_1.get_reg();
        int beforeSize = bytes.size();

        // opcode
        int body = riscv.r_type(riscv.R_TYPE, self_reg, 0, riscv.ZERO, reg_1, 0);

        riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }
}
