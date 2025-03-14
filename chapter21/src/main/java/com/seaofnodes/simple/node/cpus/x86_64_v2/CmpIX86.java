package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.Node;

// Compare immediate.  Sets flags.
public class CmpIX86 extends ImmX86 {
    CmpIX86( Node add, int imm ) { super(add,imm); }
    @Override public String op() { return _imm==0 ? "test" : "cmp"; }
    @Override public RegMask outregmap() { return x86_64_v2.FLAGS_MASK; }
    @Override public int twoAddress() { return 0; }
    @Override int opcode() { return 0x81; }
    @Override int mod() { return 7; }
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="flags" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1)));
        if( _imm != 0 ) sb.p(", #").p(_imm);
    }
}
