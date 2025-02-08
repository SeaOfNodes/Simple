package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;

public class AddFMemX86 extends MemOpX86 {
    AddFMemX86( AddFNode add, LoadNode ld , Node base, Node idx, int off, int scale, Node val ) {
        super(add,ld, base, idx, off, scale, 0, val );
    }

    @Override public RegMask regmap(int i) {
        if( i==1 ) return RegMask.EMPTY;    // Memory
        if( i==2 ) return x86_64_v2.RMASK;  // base
        if( i==3 ) return x86_64_v2.RMASK;  // index
        if( i==4 ) return x86_64_v2.XMASK;  // value
        throw Utils.TODO();
    }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }


    // General form: "add  dst = src + [base + idx<<2 + 12]"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ");
        sb.p(val()==null ? "#"+_imm : code.reg(val())).p(" + ");
        asm_address(code,sb);
    }
    @Override public String op() { return "addf"+_sz; }
}
