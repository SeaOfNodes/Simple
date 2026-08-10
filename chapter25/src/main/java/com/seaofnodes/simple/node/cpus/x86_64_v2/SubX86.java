package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.node.Node;

public class SubX86 extends RegX86 {
    SubX86( Node add ) { super(add); }
    @Override public String op() { return "sub"; }
    @Override public String glabel() { return "-"; }
    @Override int opcode() { return 0x2B; }
}
