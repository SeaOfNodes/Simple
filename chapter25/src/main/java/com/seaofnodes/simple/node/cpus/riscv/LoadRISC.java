package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;

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
        short dst = enc.reg(this );
        short ptr = enc.reg(ptr());
        int op = dst >= riscv.F_OFFSET ? riscv.OP_LOADFP  : riscv.OP_LOAD;
        if( dst >= riscv.F_OFFSET  ) dst -= riscv.F_OFFSET;
        enc.add4(riscv.i_type(op, dst, func3(), ptr, _off));
    }
    @Override int func3() {
        int func3 = -1;
        // no unsigned flavour for store, so both signed and unsigned trigger the same
        Type declType = declType();
        if( declType == TypeInteger. I8 ) func3=0; // LB
        if( declType == TypeInteger.I16 ) func3=1; // LH
        if( declType == TypeInteger.I32 ) func3=2; // LW
        if( declType == TypeInteger.BOT ) func3=3; // LD
        if( declType == TypeInteger. U8 ) func3=4; // LBU
        if( declType == TypeInteger.BOOL) func3=4; // LBU
        if( declType == TypeInteger.U16 ) func3=5; // LHU
        if( declType == TypeInteger.U32 ) func3=6; // LWU
        if( declType instanceof TypeInteger ti ) {
            if( -128 <= ti._min && ti._max < 128 ) func3 = 0; // LB
        }

        // float
        if( declType == TypeFloat.F32) func3 = 2; // fLW   fSW
        if( declType == TypeFloat.F64) func3 = 3; // fLD   fSD

        if( declType instanceof TypeMemPtr) func3 = 3; // 8 byte pointers (pick ld)
        if( func3 == -1 ) throw Utils.TODO();
        return func3;
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(",");
        asm_address(code,sb);
    }
}
