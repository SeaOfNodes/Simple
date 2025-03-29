package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.LoadNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.TypeFloat;

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
    @Override public RegMask outregmap() { return arm.MEM_MASK; }
    // ldr(immediate - unsigned offset) | ldr(register)
    @Override public void encoding( Encoding enc ) {
        if(_declaredType == TypeFloat.F32 || _declaredType == TypeFloat.F64) {
            ldst_encode(enc, arm.OPF_LOAD_IMM, arm.OPF_LOAD_R, this, true);
        } else {
            ldst_encode(enc, arm.OP_LOAD_IMM, arm.OP_LOAD_R, this, false);
        }
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(",");
        asm_address(code,sb);
    }
}
