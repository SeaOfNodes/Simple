package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class LeaX86 extends MachConcreteNode implements MachNode {
    final int _scale;
    final long _offset;
    LeaX86( Node add, Node base, Node idx, int scale, long offset ) {
        super(add);
        assert scale==0 || scale==1 || scale==2 || scale==3;
        _inputs.pop();
        _inputs.pop();
        _inputs.push(base);
        _inputs.push(idx);
        _scale = scale;
        _offset = offset;
    }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return x86_64_v2.RMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // REX.W + 8D /r	LEA r64,m
        LRG lea_rg = CodeGen.CODE._regAlloc.lrg(this);
        short reg = lea_rg.get_reg();

        int beforeSize = bytes.size();

        x86_64_v2.assert_imm_32(_offset);

        LRG base_rg = CodeGen.CODE._regAlloc.lrg(in(1));

        LRG idx_rg = CodeGen.CODE._regAlloc.lrg(in(2));

        short idx_reg = -1;
        if(idx_rg != null) idx_reg = idx_rg.get_reg();

        assert idx_reg != x86_64_v2.RSP;
        // base is null
        // just do: [(index * s) + disp32]
        if(in(1) == null) {
            bytes.write(x86_64_v2.rex(reg, 0, idx_reg));
            bytes.write(0x8D); // opcode

            bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.INDIRECT, reg, 0x04));
            bytes.write(x86_64_v2.sib(_scale, idx_reg, x86_64_v2.RBP));
            x86_64_v2.imm((int)_offset, 32, bytes);
            // early return
            return bytes.size() - beforeSize;
        }

        short base_reg = base_rg.get_reg();
        bytes.write(x86_64_v2.rex(reg, base_reg, idx_reg));
        bytes.write(0x8D); // opcode

        // rsp is hard-coded here(0x04)
        x86_64_v2.indirectAdr(_scale, idx_reg, base_reg, (int)_offset, reg, bytes);

        return bytes.size() - beforeSize;
    }

    // General form: "lea  dst = base + 4*idx + 12"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ");
        if( in(1) != null )
            sb.p(code.reg(in(1))).p(" + ");
        sb.p(code.reg(in(2))).p("*").p(_scale);
        if( _offset!=0 ) sb.p(" + #").p(_offset);
    }

    @Override public String op() { return "lea"; }
}
