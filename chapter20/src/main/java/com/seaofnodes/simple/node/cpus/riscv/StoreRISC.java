package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.MachConcreteNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StoreNode;
import java.io.ByteArrayOutputStream;
import java.util.BitSet;


// sw rs2,offset(rs1)
public class StoreRISC extends MemOpRISC {
    StoreRISC( StoreNode st) {
        super(st, st);
    }

    // Wider mask to store both GPRs and FPRs
    @Override public RegMask regmap(int i) {
        if( i==1 ) return riscv.MEM_MASK;
        if( i==2 ) return riscv.RMASK;
        throw Utils.TODO();
    }


    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {

    }

    @Override public String op() { return "st"+_sz; }
}
