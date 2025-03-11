package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.LoadNode;
import com.seaofnodes.simple.node.Node;

// Load memory addressing on ARM
// Support imm, reg(direct), or reg+off(indirect) addressing
// Base = base - base pointer, offset is added to base
// idx  = null
// off  = off - offset added to base

public class LoadARM extends MemOpARM{
    LoadARM(LoadNode ld,Node base, Node idx, int off) {
        super(ld, base, idx, off, 0);
    }
    @Override public String op() { return "ld"+_sz; }
    @Override public RegMask regmap(int i) { return arm.RMASK; }
    @Override public RegMask outregmap() { return arm.MEM_MASK; }

    @Override public void encoding( Encoding enc ) {
        short self = enc.reg(this);
        short base = enc.reg(in(2)); // base always must be provided
        short indx = enc.reg(in(3)); // might be -1

        // load from memory into load_reg
        int body = indx== -1
            ? arm.load_str_imm(1986, _off, base, self)
            : arm.indr_adr(1987, indx, arm.STORE_LOAD_OPTION.SXTW, 0, base, self);
        enc.add4(body);
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(",");
        asm_address(code,sb);
    }

}
