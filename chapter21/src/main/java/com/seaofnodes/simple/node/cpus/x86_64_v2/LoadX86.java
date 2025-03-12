package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.LoadNode;
import com.seaofnodes.simple.node.Node;

public class LoadX86 extends MemOpX86 {
    LoadX86( LoadNode ld, Node base, Node idx, int off, int scale ) {
        super(ld,ld, base, idx, off, scale, 0);
    }
    @Override public String op() { return "ld"+_sz; }
    @Override public RegMask outregmap() { return x86_64_v2.MEM_MASK; }
    @Override public void encoding( Encoding enc ) {
        // REX.W + 8B /r	MOV r64, r/m64
        short dst = enc.reg(this );
        short ptr = enc.reg(ptr());
        short idx = enc.reg(idx());
        bytes.write(x86_64_v2.rex(reg, base_reg, idx_reg));
        bytes.write(0x8B); // opcode

        // rsp is hard-coded here(0x04)
        // includes modrm internally
        x86_64_v2.indirectAdr(_scale, idx_reg, base_reg, _off, reg, bytes);
    }

    // General form: "ldN  dst,[base + idx<<2 + 12]"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(",");
        asm_address(code,sb);
    }
}
