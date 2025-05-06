package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.util.Utils;

public class ShlX86 extends RegX86 {
    ShlX86( Node add ) { super(add); }
    @Override public String op() { return "shl"; }
    @Override public String glabel() { return "<<"; }
    @Override public RegMask regmap(int i) {
        if (i == 1) return x86_64_v2.WMASK;
        if (i == 2) return x86_64_v2.RCX_MASK;
        throw Utils.TODO();
    }
    @Override int opcode() { return 0xD3; }
    @Override public final void encoding( Encoding enc ) {
        X86.unary(enc,opcode(),4,enc.reg(this));
    }
}
