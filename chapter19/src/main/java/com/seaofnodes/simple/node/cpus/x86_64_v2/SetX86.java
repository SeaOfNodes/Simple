package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

// Compare immediate.  Sets flags.
public class SetX86 extends MachConcreteNode implements MachNode {
    final String _bop;          // One of <,<=,==
    // Constructor expects input is an X86 and not an Ideal node.
    SetX86( MachConcreteNode cmp, String bop ) {
        super(cmp);
        _inputs.push(cmp);
        _bop = bop;
    }

    @Override public RegMask regmap(int i) { assert i==1; return x86_64_v2.FLAGS_MASK; }
    @Override public RegMask outregmap() { return x86_64_v2.WMASK; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this));
        String src = code.reg(in(1));
        if( src!="FLAGS" )  sb.p(" = ").p(src);
    }

    @Override public String op() { return "set"+_bop; }

}
