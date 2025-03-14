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
    StoreRISC( StoreNode st, int off, Node val ) { super(st, off, val); }
    @Override public String op() { return "st"+_sz; }
    @Override public RegMask regmap(int i) {
        // 0 - ctrl
        // 1 - mem
        if( i==2 ) return riscv.RMASK; // ptr
        // 2 - index
        if( i==4 ) return riscv.MEM_MASK; // Wide mask to store GPR and FPR
        throw Utils.TODO();
    }
    @Override public RegMask outregmap() { return null; }

    @Override public void encoding( Encoding enc ) {
        short val = xreg(enc);
        short ptr = enc.reg(ptr());
        int body = riscv.s_type(opcode(enc),  ptr, func3()&3, val, _off);
        enc.add4(body);
    }

    @Override public void asm(CodeGen code, SB sb) {
        asm_address(code,sb).p(",");
        if( val()==null ) sb.p("#").p("0");
        else sb.p(code.reg(val()));
    }
}
