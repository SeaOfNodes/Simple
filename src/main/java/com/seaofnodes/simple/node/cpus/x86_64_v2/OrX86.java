package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.node.Node;

public class OrX86 extends RegX86 {
    OrX86( Node add ) { super(add); }
    @Override public String op() { return "or"; }
    @Override public String glabel() { return "|"; }
    @Override int opcode() { return 0x0B; }
    @Override public boolean commutes() { return true; }
}
