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
    @Override public boolean isLoad() { return false; }
    @Override public RegMask outregmap() { return null; }
    @Override public void encoding( Encoding enc ) {
        if(_declaredType == TypeFloat.F32 || _declaredType == TypeFloat.F64) {
            ldst_encode(enc, arm.OPF_STORE_IMM,arm.OPF_STORE_R, val(), true);
        } else {
            ldst_encode(enc, arm.OP_STORE_IMM, arm.OP_STORE_R, val(), false);
        }
    }
    @Override public void asm(CodeGen code, SB sb) {
        asm_address(code,sb).p(",");
        if( val()==null ) sb.p("#").p(_imm);
        else sb.p(code.reg(val()));
    }
}
