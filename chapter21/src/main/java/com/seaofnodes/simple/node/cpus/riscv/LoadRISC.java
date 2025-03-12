package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;

// Load memory addressing on RISC
// Support imm, reg(direct), or reg+off(indirect) addressing
// Base = base - base pointer, offset is added to base
// idx  = null
// off  = off - imm12 added to base
public class LoadRISC extends MemOpRISC {
    LoadRISC(LoadNode ld, int off) {
        super(ld, ld.ptr(), off, null);
    }
    @Override public String op() { return "ld" +_sz; }
    @Override public RegMask regmap(int i) { return riscv.RMASK; }
    @Override public RegMask outregmap() { return riscv.MEM_MASK; }

    @Override public void encoding( Encoding enc ) {
        int op = 3; // 7 bits, 00 000 11 or 00 001 11 for FP
        short dst = enc.reg(this );
        if( dst >= riscv.F_OFFSET ) {
            dst -= riscv.F_OFFSET;
            op = 7;
        }

        int func3 = -1;
        if( _declaredType == TypeInteger. I8 ) func3=0; // LB
        if( _declaredType == TypeInteger.I16 ) func3=1; // LH
        if( _declaredType == TypeInteger.I32 ) func3=2; // LW
        if( _declaredType == TypeInteger.INT ) func3=3; // LD
        if( _declaredType == TypeInteger. U8 ) func3=4; // LBU
        if( _declaredType == TypeInteger.BOOL) func3=4; // LBU
        if( _declaredType == TypeInteger.U16 ) func3=5; // LHU
        if( _declaredType == TypeInteger.U32 ) func3=6; // LWU
        if( func3 == -1 ) throw Utils.TODO();

        short ptr = enc.reg(ptr());
        int body = riscv.i_type(op,  dst, func3, ptr, _off);
        enc.add4(body);
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(",");
        asm_address(code,sb);
    }
}
