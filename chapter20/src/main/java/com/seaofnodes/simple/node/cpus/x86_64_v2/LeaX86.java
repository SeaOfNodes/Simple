package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class LeaX86 extends MachConcreteNode implements MachNode {
    final int _scale;
    final long _offset;
    LeaX86( Node add, Node base, Node idx, int scale, long offset ) {
        super(add);
        assert scale==1 || scale==2 || scale==4 || scale==8;
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
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // REX.W + 8D /r	LEA r64,m
        LRG lea_rg = CodeGen.CODE._regAlloc.lrg(this);
        short reg = lea_rg.get_reg();

        int beforeSize = bytes.size();

        LRG base_rg = CodeGen.CODE._regAlloc.lrg(in(1));

        LRG idx_rg = CodeGen.CODE._regAlloc.lrg(in(2));

        short base_reg = base_rg.get_reg();
        short idx_re = idx_rg.get_reg();

        // base is null
        // just do: [(index * s) + disp32]
        if(in(1) == null) {
            bytes.write(x86_64_v2.rex(reg, idx_re, 0));
            bytes.write(0x8D); // opcode

            bytes.write(x86_64_v2.modrm(x86_64_v2.MOD.INDIRECT_disp32, reg, 0x04));
            bytes.write(x86_64_v2.sib(_scale, idx_re, x86_64_v2.RBP));
            // early return
            return bytes.size() - beforeSize;
        }


        bytes.write(x86_64_v2.rex(reg, idx_re, base_reg));
        bytes.write(0x8D); // opcode

        // rsp is hard-coded here(0x04)
        x86_64_v2.sibAdr(_scale, idx_re, base_reg, (int)_offset, reg, bytes);

        // long truncating here BAD!!
        bytes.write((int)_offset);

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
