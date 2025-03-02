package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import java.io.ByteArrayOutputStream;

public class NewRISC extends NewNode implements MachNode {
    // A pre-zeroed chunk of memory.
    NewRISC( NewNode nnn ) { super(nnn); }
    // Size and pointer result in standard calling convention; null for all the
    // memory aliases edges
    @Override public RegMask    regmap(int i) { return i == 1 ? riscv.A0_MASK : null; }
    @Override public RegMask outregmap(int i) { return i == 1 ? riscv.A0_MASK : null; }
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // call to malloc

        LRG call_self = CodeGen.CODE._regAlloc.lrg(this);
        short rd = call_self.get_reg();

        int beforeSize = bytes.size();
        //  auipc    ra,0x0
        int body = riscv.u_type(0x17, rd, 0);
        int body2 = riscv.i_type(0x67, rd, 0, rd, 0);
        riscv.push_4_bytes(body, bytes);
        riscv.push_4_bytes(body2, bytes);

        return bytes.size() - beforeSize;
    }

    // General form: "alloc #bytes"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(size()));
    }

    @Override public String op() { return "alloc"; }
}
