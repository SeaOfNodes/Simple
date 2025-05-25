package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;

public class AddIX86 extends ImmX86 {
    AddIX86( Node add, int imm ) { super(add,imm); }
    @Override public String op() {
        return _imm == 1  ? "inc" : (_imm == -1 ? "dec" : "addi");
    }
    @Override public String glabel() { return "+"; }
    @Override int opcode() { return 0x81; }
    @Override int mod() { return 0; }
    @Override public final void encoding( Encoding enc ) {
        if( _imm== 1 || _imm== -1 ) {
            short dst = enc.reg(this); // Also src1
            enc.add1( x86_64_v2.rex(0,dst,0));
            enc.add1(0xFF);
            enc.add1( x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, _imm==1 ? 0 : 1, dst) );
        } else super.encoding(enc);
    }
}
