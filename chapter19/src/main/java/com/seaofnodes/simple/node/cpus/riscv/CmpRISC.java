package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import java.io.ByteArrayOutputStream;

// Compare.  Sets flags.(RFLAGS)
public class CmpRISC extends  MachConcreteNode implements MachNode{
    CmpRISC( Node cmp ) { super(cmp); }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }


    // General form: "cmp  rs1, rs2"
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="FLAGS" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1))).p(", ").p(code.reg(in(2)));
    }

    @Override public RegMask regmap(int i) { assert i==1 || i==2; return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.FLAGS_MASK; }
}
