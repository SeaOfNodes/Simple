package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import java.io.ByteArrayOutputStream;

public class LeaRISC extends MachConcreteNode implements MachNode {
    final int _scale;
    final long _offset;
    LeaRISC(Node add, Node base, Node idx, int scale, long offset ) {
        super(add);
        assert scale==1 || scale==2 || scale==4 || scale==8;
        _inputs.pop();
        _inputs.pop();
        _inputs.push(base);
        _inputs.push(idx);
        _scale = scale;
        _offset = offset;
    }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return riscv.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.RMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    // General form: "lea  dst = base + 4*idx + 12"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ");
        if( in(1) != null )
            sb.p(code.reg(in(1))).p(" + ");
        sb.p(code.reg(in(2))).p("*").p(_scale);
        if( _offset!=0 ) sb.p(" + #").p(_offset);
    }

    @Override public String op() { return "lea"; }
}
