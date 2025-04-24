package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFloat;

public class CmpMemX86 extends MemOpX86 {
    final boolean _swap;      // Op switched LHS, RHS
    CmpMemX86( BoolNode bool, LoadNode ld, Node base, Node idx, int off, int scale, int imm, Node val, boolean swap ) {
        super(bool,ld, base, idx, off, scale, imm, val );
        _swap = swap;
        assert !_swap || val() == null || bool.op()=="==" || bool.op()=="!="; // Cannot swap with immediate
    }
    @Override public String op() { return ((val()==null && _imm==0) ? "test" : "cmp") + _sz; }
    @Override public RegMask outregmap() { return x86_64_v2.FLAGS_MASK; }
    @Override public void encoding( Encoding enc ) {
        short ptr=enc.reg(ptr()), idx=enc.reg(idx()), src=enc.reg(val());
        if( src==-1 ) {
            assert !_swap;
            X86.cmpImm(enc,_declaredType.log_size(),_imm,ptr,idx,_off,_scale);
        } else encVal(enc,_declaredType,ptr,idx,src,_off,_scale,_swap);
    }

    static void encVal(Encoding enc, Type decl, short ptr, short idx, short src, int off, int scale, boolean _swap) {
        assert !(decl instanceof TypeFloat); // Selected separately as CmpFX86.
        X86.cmpMem(enc,decl.log_size(),src,ptr,idx,off,scale,_swap);
    }

    // General form: "cmp  dst = src, [base + idx<<2 + 12]"
    @Override public void asm(CodeGen code, SB sb) {
        String dst = code.reg(this);
        if( dst!="flags" )  sb.p(dst).p(" = ");
        if( _swap ) asm_address(code,sb).p(",");
        if( val()==null ) {
            if( _imm!=0 )  sb.p("#").p(_imm);
        } else {
            sb.p(code.reg(val()));
        }
        if( !_swap ) asm_address(code,sb.p(", "));
    }
}
