package com.seaofnodes.simple.node.cpus.riscv;


import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class LoadRISC extends MachConcreteNode implements MachNode{
    LoadRISC(LoadNode ld) {
        super(ld);
    }

    @Override public RegMask regmap(int i) {
        return riscv.RMASK;
    }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.RMASK; }


    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
    }

    @Override public String op() {
        return "lw";
    }
}

