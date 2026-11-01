package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;

public class AddFMemX86 extends MemOpX86 {
    AddFMemX86( AddNode add, LoadNode ld , Node base, Node idx, int off, int scale, Node val ) {
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
        X86.memory(enc,0xF2,0x0F58,false,enc.reg(this)-x86_64_v2.XMM_OFFSET,enc.reg(ptr()),enc.reg(idx()),_off,_scale);
    }

    // General form: "add  dst = src + [base + idx<<2 + 12]"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ");
        sb.p(val()==null ? "#"+_imm : code.reg(val())).p(" + ");
        asm_address(code,sb);
    }
}
