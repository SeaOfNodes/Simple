package com.seaofnodes.simple.node.cpus.riscv;


import com.seaofnodes.simple.CodeGen;
import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.node.*;

public class CmpMemRISC extends MemOpRISC{
    final boolean _invert;      // Op switched LHS, RHS
    CmpMemRISC( BoolNode bool, LoadNode ld , Node base, Node idx, int off, int scale, int imm, Node val, boolean invert ) {
        super(bool,ld, base, idx, off, scale, imm, val );
        _invert = invert;
    }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return riscv.RMASK; }

    // General form: "cmp  dst = src, [base + idx<<2 + 12]"
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="FLAGS" )  sb.p(dst).p(" = ");
        if( _invert ) asm_address(code,sb).p(",");
        if( val()==null ) {
            if( _imm!=0 )  sb.p("#").p(_imm);
        } else {
            sb.p(code.reg(val()));
        }
        if( !_invert ) asm_address(code,sb.p(", "));
    }

    @Override public String op() { return ((val()==null && _imm==0) ? "test" : "cmp") + _sz; }
}
