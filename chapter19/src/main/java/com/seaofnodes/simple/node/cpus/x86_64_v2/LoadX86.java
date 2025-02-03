package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.CodeGen;
import com.seaofnodes.simple.RegMask;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.node.LoadNode;
import com.seaofnodes.simple.node.Node;
import java.util.BitSet;

public class LoadX86 extends MemOpX86 {
    LoadX86( LoadNode ld, Node base, Node idx, int off, int scale ) {
        super(ld,ld._name,ld._alias,ld._declaredType,ld._loc, base, idx, off, scale, 0);
    }

    @Override public String op() { return "ldX"; }

    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }


    // General form: "ldN  dst,[base + idx<<2 + 12]"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(",");
        sb.p("[").p(code.reg(ptr()));
        if( idx() != null ) {
            sb.p("+").p(code.reg(idx()));
            if( _scale != 0 )
                sb.p("<<").p(_scale);
        }
        if( _off != 0 )
            sb.p("+").p(_off);
        sb.p("]");
    }
}
