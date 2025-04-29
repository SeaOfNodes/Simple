package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class AddFMemX86 extends MemOpX86 {
    AddFMemX86( AddFNode add, LoadNode ld , Node base, Node idx, int off, int scale, Node val ) {
        super(add,ld, base, idx, off, scale, 0, val );
    }
    @Override public String op() { return "addf"+_sz; }
    @Override public RegMask regmap(int i) {
        if( i==1 ) return null;            // Memory
        if( i==2 ) return x86_64_v2.RMASK; // base
        if( i==3 ) return x86_64_v2.RMASK; // index
        if( i==4 ) return x86_64_v2.XMASK; // value
        throw Utils.TODO();
    }
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }
    @Override public int twoAddress() { return 4; }
    @Override public void encoding( Encoding enc ) {
        //  addsd xmm0, DWORD PTR [rdi+0xc]
        short dst = enc.reg(this );
        short ptr = enc.reg(ptr());
        short idx = enc.reg(idx());
        // F opcode
        enc.add1(0xF2);
        // rex prefix must come next (REX.W is not set)
        x86_64_v2.rexF(dst, ptr, idx, false, enc);

        // FP ADD
        enc.add1(0x0F).add1(0x58);

        x86_64_v2.indirectAdr(_scale, idx, ptr, _off, dst, enc);
    }

    // General form: "add  dst = src + [base + idx<<2 + 12]"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ");
        sb.p(val()==null ? "#"+_imm : code.reg(val())).p(" + ");
        asm_address(code,sb);
    }
}
