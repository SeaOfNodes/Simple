package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class AddMemX86 extends MemOpX86 {
    AddMemX86( AddNode add, LoadNode ld , Node base, Node idx, int off, int scale, int imm, Node val ) {
        super(add,ld, base, idx, off, scale, imm, val );
    }
    @Override public String op() { return "add"+_sz; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    @Override public int twoAddress() { return 4; }
    @Override public void encoding( Encoding enc ) {
        // add something to register from memory
        // add   eax,DWORD PTR [rdi+0xc]
        // REX.W + 03 /r	ADD r64, r/m64
        short dst = enc.reg(this );
        short ptr = enc.reg(ptr());
        short idx = enc.reg(idx());

        enc.add1(x86_64_v2.rex(dst, ptr, idx));
        // opcode
        enc.add1(0x03);

        x86_64_v2.indirectAdr(_scale, idx, ptr, _off, dst, enc);
    }

    // General form: "add  dst += [base + idx<<2 + 12]"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" += ");
        asm_address(code,sb);
    }
}
