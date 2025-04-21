package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.node.Node;

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
            X86.unary(enc,0xFF,_imm==1 ? 0 : 1,dst);
        } else super.encoding(enc);
    }
}
