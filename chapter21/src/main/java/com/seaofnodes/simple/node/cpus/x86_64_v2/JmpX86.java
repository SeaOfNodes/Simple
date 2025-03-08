package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

// Jump on flags, uses flags
public class JmpX86 extends IfNode implements MachNode {
    final String _bop;
    JmpX86( IfNode iff, String bop ) {
        super(iff);
        _bop = bop;
    }

    @Override public String label() { return op(); }

    @Override public void postSelect() {
        Node set = in(1);
        Node cmp = set.in(1);
        // Bypass an expected Set and just reference the cmp directly
        if( set instanceof SetX86 && (cmp instanceof CmpX86 || cmp instanceof CmpIX86 || cmp instanceof CmpMemX86 || cmp instanceof CmpFX86) )
            _inputs.set(1,cmp);
        else
            throw Utils.TODO();
    }
    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.FLAGS_MASK; }
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        String src = code.reg(in(1));
        if( src!="flags" )  sb.p(src);
    }

    @Override public String op() { return "j"+_bop; }

    @Override public String comment() {
        return "L"+cproj(1)._nid+", L"+cproj(0)._nid;
    }
}
