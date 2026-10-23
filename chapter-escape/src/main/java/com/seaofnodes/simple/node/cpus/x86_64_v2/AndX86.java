package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.node.Node;

public class AndX86 extends RegX86 {
    AndX86( Node add ) { super(add); }
    @Override public String op() { return "and"; }
    @Override public String glabel() { return "&"; }
    @Override int opcode() { return 0x23; }
    @Override public boolean commutes() { return true; }
}
