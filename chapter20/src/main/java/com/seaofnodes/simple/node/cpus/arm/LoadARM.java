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

    @Override public RegMask regmap(int i) {
        return arm.RMASK;
    }

    // Wide mask loads both ints and floats; encoding varies.
    @Override public RegMask outregmap() { return arm.MEM_MASK; }


    @Override public String op() { return "ld"+_sz; }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(",");
        asm_address(code,sb);
    }

}
