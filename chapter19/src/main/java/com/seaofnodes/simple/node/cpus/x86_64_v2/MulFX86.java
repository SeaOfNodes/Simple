package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFloat;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;

public class MulFX86 extends MachConcreteNode implements MachNode {
    Node _mulf;
    MulFX86( Node mulf) {
        super(mulf);
        _debug = _inputs.last();
        _mulf = mulf;
    }

    // Register mask allowed on input i.
    @Override public RegMask regmap(int i) { assert i==1 || i==2; return x86_64_v2.XMASK; }
    // Register mask allowed as a result.  0 for no register.
    @Override public RegMask outregmap() { return x86_64_v2.XMASK; }
    // Output is same register as input#1
    @Override public int twoAddress() { return 1; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public String comment() {
        return _debug.print();
    }

    // General form: "mulf  dst *= src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1)));
    }

    @Override public String op() {
        TypeFloat type = (TypeFloat)(_mulf._type);
        if(type._sz == 64) return "mulsd";
        return "mulss";
    }
}
