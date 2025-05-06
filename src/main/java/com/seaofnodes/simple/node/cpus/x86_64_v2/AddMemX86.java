package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;

public class AddMemX86 extends MemOpX86 {
    AddMemX86( AddNode add, LoadNode ld , Node base, Node idx, int off, int scale, int imm, Node val ) {
        super(add,ld, base, idx, off, scale, imm, val );
    }
    @Override public String op() { return "add"+_sz; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    @Override public int twoAddress() { return 4; }
    @Override public void encoding( Encoding enc ) {
        X86.memory(enc,0,0x03,true,enc.reg(this),enc.reg(ptr()),enc.reg(idx()),_off,_scale);
    }

    // General form: "add  dst += [base + idx<<2 + 12]"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" += ");
        asm_address(code,sb);
    }
}
