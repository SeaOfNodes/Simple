package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

// Compare immediate.  Sets flags.(RFLAGS)
public class CmpIX86 extends MachConcreteNode implements MachNode {
    final int _imm;
    final String _bop;
    CmpIX86( BoolNode bool, TypeInteger ti ) {
        super(bool);
        _inputs.pop(); // Toss ideal ConstantNode away, embedded into machine op
        _bop = bool.op();       // One of <,<=,==
        _imm = (int)ti.value();
        assert _imm == ti.value();
    }
    CmpIX86( Node cmp, double ignore ) {
        super(cmp);
        _bop = "==";
        _imm = 0;
    }

    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.RMASK; }
    @Override public RegMask outregmap() { return x86_64_v2.FLAGS_MASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="FLAGS" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1)));
        if( _imm != 0 ) sb.p(", #").p(_imm);
    }

    @Override public String op() { return _imm==0 ? "test" : "cmp"; }

}
