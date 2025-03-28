package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

// Load memory addressing on RISC
// Support imm, reg(direct), or reg+off(indirect) addressing
// Base = base - base pointer, offset is added to base
// idx  = null
// off  = off - imm12 added to base
public class LoadRISC extends MemOpRISC {
    public LoadRISC(LoadNode ld, Node base, int off) { super(ld, base, off, null); }
    @Override public String op() { return "ld" +_sz; }
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.MEM_MASK; }
    @Override public void encoding( Encoding enc ) {
        short dst = xreg(enc);
        short ptr = enc.reg(ptr());
        int body = riscv.i_type(opcode(enc), dst, func3(), ptr, _off);
        enc.add4(body);
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(",");
        asm_address(code,sb);
    }
}
