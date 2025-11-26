package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.node.Node;

public class AddX86 extends RegX86 {
    AddX86( Node add ) { super(add); }
    @Override public String op() { return "add"; }
    @Override public String glabel() { return "+"; }
    @Override int opcode() { return 0x03; }
    @Override public boolean commutes() { return true; }
}
