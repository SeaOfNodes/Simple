package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.CodeGen;
import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.node.LoadNode;
import com.seaofnodes.simple.node.Node;

public class LoadRISC extends MemOpRISC{
    LoadRISC(LoadNode ld, Node base, Node idx, int off, int scale) {
        super(ld, ld, base, idx, off, scale, 0);
    }

    @Override public String op() { return "ld"+_sz; }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.RMASK; }

    // General form: "ldN  dst,[base + idx<<2 + 12]"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(",");
        asm_address(code,sb);
    }
}

