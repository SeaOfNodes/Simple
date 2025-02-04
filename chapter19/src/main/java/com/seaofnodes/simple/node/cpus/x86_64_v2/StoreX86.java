package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.CodeGen;
import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.StoreNode;
import java.util.BitSet;

public class StoreX86 extends MemOpX86 {
    StoreX86( StoreNode st, Node base, Node idx, int off, int scale, int imm, Node val ) {
        super(st,st._name,st._alias,st._declaredType,st._loc, base, idx, off, scale, imm, val);
    }

    @Override public String op() { return "st"+_sz; }

    @Override public StringBuilder _printMach(StringBuilder sb, BitSet visited) {
        return sb.append(".").append(_name).append("=").append(val()==null ? _imm : val()).append(";");
    }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return RegMask.EMPTY; }

    // General form: "stN  [base + idx<<2 + 12],val"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p("[").p(code.reg(ptr()));
        if( idx() != null ) {
            sb.p("+").p(code.reg(idx()));
            if( _scale != 0 )
                sb.p("<<").p(_scale);
        }
        if( _off != 0 )
            sb.p("+").p(_off);
        sb.p("],");
        if( val()==null ) sb.p("#").p(_imm);
        else sb.p(code.reg(val()));
    }
}
