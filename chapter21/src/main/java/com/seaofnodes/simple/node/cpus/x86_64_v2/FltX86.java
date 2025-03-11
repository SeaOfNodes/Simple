package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;

public class FltX86 extends ConstantNode implements MachNode {
    FltX86(ConstantNode con ) { super(con); }

    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return null; }
    // General int registers
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // Simply move the constant into a FPR
        // movsd xmm, [rip + 0]
        // F2 0F 10 /r MOVSD xmm1, m64
        LRG fpr_con = CodeGen.CODE._regAlloc.lrg(this);
        short fpr_reg = fpr_con.get_reg();

        int beforeSize = bytes.size();
        bytes.write(x86_64_v2.rex(fpr_reg - x86_64_v2.XMM_OFFSET, 0, 0));

        // Fopcode
        bytes.write(0xF2);
        bytes.write(0x0F);
        bytes.write(0x10);

        // hard-code rip here
        bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.INDIRECT, fpr_reg - x86_64_v2.XMM_OFFSET, 0x05));
        x86_64_v2.imm(0, 32, bytes);

        return bytes.size() - beforeSize;
    }

    @Override public boolean isClone() { return true; }

    // Human-readable form appended to the SB.  Things like the encoding,
    // indentation, leading address or block labels not printed here.
    // Just something like "ld4\tR17=[R18+12] // Load array base".
    // General form: "op\tdst=src+src"
    @Override public void asm(CodeGen code, SB sb) {
        _con.print(sb.p(code.reg(this)).p(" #"));
    }

    @Override public String op() {
        return "fld";           // Some fancier encoding
    }
}
