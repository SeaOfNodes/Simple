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

public class LoadARM extends MemOpARM {
    LoadARM(LoadNode ld, Node base, Node idx, int off) {
        super(ld, base, idx, off, 0);
    }
    @Override public String op() { return "ld"+_sz; }
    @Override public RegMask outregmap() { return arm.MEM_MASK; }

    private static final int[] OP_LOADS  = new int[]{ arm.OP_LOAD_IMM_8,  arm.OP_LOAD_IMM_16,  arm.OP_LOAD_IMM_32,  arm.OP_LOAD_IMM_64, };
    private int imm_op() {
        return _declaredType == TypeFloat.F32 ? arm.OPF_LOAD_IMM_32
            :  _declaredType == TypeFloat.F64 ? arm.OPF_LOAD_IMM_64
            :  OP_LOADS[_declaredType.log_size()];
    }

    private static final int[] OP_LOAD_RS  = new int[]{ arm.OP_LOAD_R_8 , arm.OP_LOAD_R_16 , arm.OP_LOAD_R_32 , arm.OP_LOAD_R_64,  };

    // ldr(immediate - unsigned offset) | ldr(register)
    @Override public void encoding( Encoding enc ) {
        ldst_encode(enc, imm_op(), OP_LOAD_RS[_declaredType.log_size()], this, size());
    }
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(",");
        asm_address(code,sb);
    }
}
