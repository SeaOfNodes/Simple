package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.ConstantNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.node.cpus.riscv.riscv;

public class TFPARM extends ConstantNode implements MachNode {
    TFPARM( ConstantNode con ) { super(con); }
    // Register mask allowed on input i.  0 for no register.
    @Override public RegMask regmap(int i) { return null; }

    // General int registers
    @Override public RegMask outregmap() { return arm.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // Todo: relocs
        // LDR (immediate)
        LRG tfp_rg = CodeGen.CODE._regAlloc.lrg(this);

        short rd = tfp_rg.get_reg();

        int beforeSize = bytes.size();

        int body = arm.load_adr(1986,0, 0, rd);

        riscv.push_4_bytes(body, bytes);

        return bytes.size() - beforeSize;
    }

    // Human-readable form appended to the SB.  Things like the encoding,
    // indentation, leading address or block labels not printed here.
    // Just something like "ld4\tR17=[R18+12] // Load array base".
    // General form: "op\tdst=src+src"
    @Override public void asm(CodeGen code, SB sb) {
        String reg = code.reg(this);
        _con.print(sb.p(reg).p(" #"));
    }

    @Override public String op() {
        return "ldx";           // Some fancier encoding
    }
}
