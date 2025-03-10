package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;

public class AddMemX86 extends MemOpX86 {
    AddMemX86( AddNode add, LoadNode ld , Node base, Node idx, int off, int scale, int imm, Node val ) {
        super(add,ld, base, idx, off, scale, imm, val );
    }

    @Override public String op() { return "add"+_sz; }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    // Output is same register as val()
    @Override public int twoAddress() { return 4; }

    // General form: "add  dst = src + [base + idx<<2 + 12]"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ");
        sb.p(val()==null ? "#"+_imm : code.reg(val())).p(" + ");
        asm_address(code,sb);
    }
}
