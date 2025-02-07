package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
import java.io.ByteArrayOutputStream;
import java.util.BitSet;

public class CallX86 extends CallNode implements MachNode {
    final TypeFunPtr _tfp;
    final String _name;
    CallX86( CallNode call, TypeFunPtr tfp ) {
        super(call);
        _inputs.pop(); // Pop constant target
        assert tfp.isConstant();
        _tfp = tfp;
        _name = CodeGen.CODE.link(tfp)._name;
    }

    @Override public String label() { return op(); }
    @Override public RegMask regmap(int i) {
        return x86_64_v2.callInMask(tfp(),i); // Normal argument
    }
    @Override public RegMask outregmap() { return x86_64_v2.RET_MASK; }

    @Override public String name() { return _name; }
    @Override public TypeFunPtr tfp() { return _tfp; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(_name);
    }

    @Override public String op() { return "call"; }

}
