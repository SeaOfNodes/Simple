package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.node.Node;

public class ShlIX86 extends ImmX86 {
    ShlIX86( Node add, int imm ) { super(add,imm); }
    @Override public String op() { return "shli"; }
    @Override public String glabel() { return "<<"; }
    @Override int opcode() { return 0xC1; }
    @Override int mod() { return 4; }
}
