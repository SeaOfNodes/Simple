package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.Node;

public class CmpX86 extends RegX86 {
    CmpX86( Node add ) { super(add); }
    @Override public String op() { return "cmp"; }
    @Override int opcode() { return 0x3B; }
    @Override public RegMask outregmap() { return x86_64_v2.FLAGS_MASK; }
    @Override public int twoAddress() { return 0; }
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="flags" )  sb.p(dst).p(" = ");
        sb.p(code.reg(in(1))).p(", ").p(code.reg(in(2)));
    }
}
