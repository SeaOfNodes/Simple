package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

// Compare immediate.  Sets flags.
public class JmpX86 extends IfNode implements MachNode {
    final String _bop;
    JmpX86( IfNode iff, MachConcreteNode pred, String bop ) { super(iff.in(0),pred); _bop = bop; }

    @Override public String label() { return op(); }
    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.FLAGS_MASK; }
    @Override public RegMask outregmap() { return RegMask.EMPTY; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        String src = code.reg(in(1));
        if( src!="FLAGS" )  sb.p(src);
    }

    @Override public String op() { return "j"+_bop; }

}
