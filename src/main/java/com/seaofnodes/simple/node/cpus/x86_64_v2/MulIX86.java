package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.node.Node;

public class MulIX86 extends ImmX86 {
    MulIX86( Node add, int imm ) { super(add,imm); }
    @Override public String op() { return "muli"; }
    @Override public String glabel() { return "*"; }
    @Override int opcode() { return 0x69; }
    @Override int mod() { return 0; }
}
