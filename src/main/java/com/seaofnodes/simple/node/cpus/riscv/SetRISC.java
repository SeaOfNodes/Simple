package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

// corresponds to slt,sltu,slti,sltiu, seqz
// RISCV doesn't have rflags.
public class SetRISC extends MachConcreteNode implements MachNode{
    final String _bop;          // One of <,<=,==
    SetRISC( Node cmp, String bop ) {
        super(cmp);
        _inputs.setLen(1);   // Pop the cmp inputs
        // Replace with the matched cmp
        _inputs.push(cmp);
        _bop = bop;
    }
    @Override public RegMask regmap(int i) { assert i==1; return riscv.FLAGS_MASK; }
    @Override public RegMask outregmap() { return riscv.RMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this));
        String src = code.reg(in(1));
        if( src!="FLAGS" )  sb.p(" = ").p(src);
    }

    @Override public String op() { return "set"+_bop; }
}
