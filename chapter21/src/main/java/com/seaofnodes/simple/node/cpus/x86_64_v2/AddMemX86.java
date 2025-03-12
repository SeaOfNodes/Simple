package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class AddMemX86 extends MemOpX86 {
    AddMemX86( AddNode add, LoadNode ld , Node base, Node idx, int off, int scale, int imm, Node val ) {
        super(add,ld, base, idx, off, scale, imm, val );
    }
    @Override public String op() { return "add"+_sz; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }
    @Override public int twoAddress() { return 4; }
    @Override public void encoding( Encoding enc ) {
                // add something to register from memory
                //  add   eax,DWORD PTR [rdi+0xc]
                // REX.W + 03 /r	ADD r64, r/m64
                short reg = -1;
                LRG mem_rg = CodeGen.CODE._regAlloc.lrg(this);
                if(mem_rg != null) reg = mem_rg.get_reg();


                int beforeSize = bytes.size();


                LRG base_rg = CodeGen.CODE._regAlloc.lrg(ptr());
                LRG idx_rg = CodeGen.CODE._regAlloc.lrg(idx());

                short base_reg = base_rg.get_reg();
                short idx_reg = -1;

                assert idx_reg != x86_64_v2.RSP;
                if(idx_rg != null) idx_reg = idx_rg.get_reg();

                bytes.write(x86_64_v2.rex(reg, base_reg, idx_reg));
                // opcode
                bytes.write(0x03);

                x86_64_v2.indirectAdr(_scale, idx_reg, base_reg, _off, reg, bytes);

                return bytes.size() - beforeSize;
    }

    // General form: "add  dst = src + [base + idx<<2 + 12]"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ");
        sb.p(val()==null ? "#"+_imm : code.reg(val())).p(" + ");
        asm_address(code,sb);
    }
}
