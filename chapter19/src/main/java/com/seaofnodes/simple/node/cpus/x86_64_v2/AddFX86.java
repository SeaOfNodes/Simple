package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFloat;

import java.io.ByteArrayOutputStream;

public class AddFX86 extends MachConcreteNode implements MachNode {
    Node _addf;
    AddFX86( Node addf) {
        super(addf);
        _debug = _inputs.last();
        _addf = addf;
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

    // General form: "addf  dst += src"
    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(this)).p(" = ").p(code.reg(in(1)));
    }

    @Override public String op() {
        TypeFloat type = (TypeFloat)(_addf._type);
        if(type._sz == 64) return "addsd";
        return "addss";
    }
}
