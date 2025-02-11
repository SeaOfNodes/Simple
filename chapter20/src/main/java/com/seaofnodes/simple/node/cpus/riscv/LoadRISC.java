package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class LoadRISC extends MemOpRISC {
    LoadRISC(LoadNode ld) {
        super(ld, ld);
    }

    @Override public RegMask regmap(int i) {
        return riscv.RMASK;
    }
    // Wide mask loads both ints and floats; encoding varies.
    @Override public RegMask outregmap() { return riscv.MEM_MASK; }


    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        throw Utils.TODO();
    }

    @Override public String op() {
        return "ld" +_sz;
    }
}
