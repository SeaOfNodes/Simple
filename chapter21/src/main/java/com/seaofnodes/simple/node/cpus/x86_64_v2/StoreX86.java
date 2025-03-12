package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StoreNode;
import java.util.BitSet;

public class StoreX86 extends MemOpX86 {
    StoreX86( StoreNode st, Node base, Node idx, int off, int scale, int imm, Node val ) {
        super(st,st, base, idx, off, scale, imm, val);
    }
    @Override public String op() { return "st"+_sz; }
    @Override public StringBuilder _printMach(StringBuilder sb, BitSet visited) {
        Node val = val();
        sb.append(".").append(_name).append("=");
        if( val==null ) sb.append(_imm);
        else val._print0(sb,visited);
        return sb.append(";");
    }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return null; }
    @Override public void encoding( Encoding enc ) {
        // REX.W + C7 /0 id	MOV r/m64, imm32 |
        // REX.W + 89 /r        MOV r/m64, r64
        short ptr = enc.reg(ptr());
        short idx = enc.reg(idx());
        short src = enc.reg(val());

        bytes.write(x86_64_v2.rex(src, ptr, idx));

        // switch on opcode depending on instruction
        if( src == -1 ) bytes.write(0xC7);  // opcode;
        else bytes.write(0x89);

        x86_64_v2.indirectAdr(_scale, idx, ptr, _off, src, bytes);
        if( src == -1 ) x86_64_v2.imm(_imm, 32, bytes);
    }

    // General form: "stN  [base + idx<<2 + 12],val"
    @Override public void asm(CodeGen code, SB sb) {
        asm_address(code,sb).p(",");
        if( val()==null ) sb.p("#").p(_imm);
        else sb.p(code.reg(val()));
    }
}
