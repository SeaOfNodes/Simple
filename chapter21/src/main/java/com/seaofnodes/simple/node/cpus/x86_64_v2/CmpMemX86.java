package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class CmpMemX86 extends MemOpX86 {
    final boolean _invert;      // Op switched LHS, RHS
    CmpMemX86( BoolNode bool, LoadNode ld , Node base, Node idx, int off, int scale, int imm, Node val, boolean invert ) {
        super(bool,ld, base, idx, off, scale, imm, val );
        _invert = invert;
    }
    @Override public String op() { return ((val()==null && _imm==0) ? "test" : "cmp") + _sz; }
    @Override public RegMask outregmap() { return x86_64_v2.FLAGS_MASK; }
    @Override public void encoding( Encoding enc ) {
                // REX.W + 81 /7 id	CMP r/m64, imm32 | REX.W + 39 /r	CMP r/m64,r64
                // CMP [mem], imm32
                boolean im_form = false;
                short reg = -1;
                LRG mem_rg = CodeGen.CODE._regAlloc.lrg(this);
                if(mem_rg != null) reg = mem_rg.get_reg();


                int beforeSize = bytes.size();


                LRG base_rg = CodeGen.CODE._regAlloc.lrg(ptr());
                LRG idx_rg = CodeGen.CODE._regAlloc.lrg(idx());

                short base_reg = base_rg.get_reg();
                short idx_reg = -1;
                if(idx_rg != null) idx_reg = idx_rg.get_reg();

                bytes.write(x86_64_v2.rex(reg, base_reg, idx_reg));
                if(in(4) != null) {
                    // val and not immediate
                    // opcode
                    bytes.write(0x81);
                } else {
                    im_form = true;
                    bytes.write(0x39);
                }

                if(im_form) {
                    reg = 7;
                }

                assert idx_reg != x86_64_v2.RSP;
                // includes modrm
                x86_64_v2.indirectAdr(_scale, idx_reg, base_reg, _off, reg, bytes);
                if(im_form) {
                    x86_64_v2.imm(_imm, 32, bytes);
                }
                return bytes.size() - beforeSize;
    }

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
}
