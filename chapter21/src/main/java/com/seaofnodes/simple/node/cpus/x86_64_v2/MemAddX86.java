package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class MemAddX86 extends MemOpX86 {
    MemAddX86( StoreNode st, Node base, Node idx, int off, int scale, int imm, Node val ) {
        super(st, st, base, idx, off, scale, imm, val );
    }
    @Override public String op() {
        return (_imm == 1 ? "inc" : (_imm == -1 ? "dec" : "add")) + _sz;
    }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return null; }
    @Override public void encoding( Encoding enc ) {
                // add something to memory
                //REX.W + 01 /r | REX.W + 81 /0 id
                // ADD [mem], imm32/reg
                boolean im_form = false;
                short reg = -1;
                LRG mem_rg = CodeGen.CODE._regAlloc.lrg(this);
                if(mem_rg != null) reg =  mem_rg.get_reg();

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
                    bytes.write(0x01);
                }

                if(im_form) {
                    reg = 0;
                }

                assert idx_reg != x86_64_v2.RSP;
                // includes modrm
                x86_64_v2.indirectAdr(_scale, idx_reg, base_reg, _off, reg, bytes);
                if(im_form) {
                    x86_64_v2.imm(_imm, 32, bytes);
                }
                return bytes.size() - beforeSize;
    }
    // General form: "add  [base + idx<<2 + 12] += src"
    @Override public void asm(CodeGen code, SB sb) {
        asm_address(code,sb);
        if( val()==null ) {
            if( _imm != 1 && _imm != -1 ) sb.p(" += #").p(_imm);
        } else sb.p(" += ").p(code.reg(val()));
    }
}
