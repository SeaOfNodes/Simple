package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;

// Store memory addressing on ARM
// Support imm, reg(direct), or reg+off(indirect) addressing
// Base = base - base pointer, offset is added to base
// idx  = null
// off  = off - offset added to base)
// imm  = imm - immediate value to store
// val  = Node of immediate value to store(null if its a constant immediate)

//e.g s.cs[0] =  67; // C
// base = s.cs, off = 4, imm = 67, val = null

// sw rs2,offset(rs1)
public class StoreRISC extends MemOpRISC {
    StoreRISC( StoreNode st, Node base, int off, Node val ) { super(st, base, off, val); }
    @Override public String op() { return "st"+_sz; }
    @Override public RegMask regmap(int i) {
        // 0 - ctrl
        if( i==1 ) return null; // mem
        if( i==2 ) return riscv.RMASK; // ptr
        // 2 - index
        if( i==4 ) return riscv.MEM_MASK; // Wide mask to store GPR and FPR
        return null; // Anti-dependence
    }
    @Override public RegMask outregmap() { return null; }

    @Override public void encoding( Encoding enc ) {
        short val = enc.reg(val());
        short ptr = enc.reg(ptr());
        int op = val >= riscv.F_OFFSET ? riscv.OP_STOREFP : riscv.OP_STORE;
        if( val >= riscv.F_OFFSET  ) val -= riscv.F_OFFSET;
        enc.add4(riscv.s_type(op, func3()&7, ptr, val == -1 ? riscv.ZERO : val, _off));
    }

    @Override public void asm(CodeGen code, SB sb) {
        asm_address(code,sb).p(",");
        if( val()==null ) sb.p("#").p("0");
        else sb.p(code.reg(val()));
    }
}
