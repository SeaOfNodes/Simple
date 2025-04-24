package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.StoreNode;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.TypeFloat;

// Store memory addressing on ARM
// Support imm, reg(direct), or reg+off(indirect) addressing
// base - base pointer, offset is added to base
// null - index never allowed (no [reg+reg] mode)
// off  - offset added to base
// imm  - immediate value to store, only if val is null
// val  - value to store or null

//e.g s.cs[0] =  67; // C
// base = s.cs, off = 4, imm = 67, val = null
public class StoreARM extends MemOpARM {
    StoreARM(StoreNode st, Node base, Node idx, int off, Node val) {
        super(st, base, idx, off, 0, val);
    }
    @Override public String op() { return "st"+_sz; }
    @Override public RegMask outregmap() { return null; }

    private static final int[] OP_STORES = new int[]{ arm.OP_STORE_IMM_8, arm.OP_STORE_IMM_16, arm.OP_STORE_IMM_32, arm.OP_STORE_IMM_64, };
    private int imm_op() {
        return _declaredType == TypeFloat.F32 ? arm.OPF_STORE_IMM_32
            :  _declaredType == TypeFloat.F64 ? arm.OPF_STORE_IMM_64
            :  OP_STORES[_declaredType.log_size()];
    }

    private static final int[] OP_STORE_RS = new int[]{ arm.OP_STORE_R_8, arm.OP_STORE_R_16, arm.OP_STORE_R_32, arm.OP_STORE_R_64, };

    @Override public void encoding( Encoding enc ) {
        ldst_encode(enc, imm_op(), OP_STORE_RS[_declaredType.log_size()], val(), size());
    }
    @Override public void asm(CodeGen code, SB sb) {
        asm_address(code,sb).p(",");
        if( val()==null ) sb.p("#").p(_imm);
        else sb.p(code.reg(val()));
    }
}
