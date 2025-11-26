package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFunPtr;
import java.io.ByteArrayOutputStream;

public class CallRISC extends CallNode implements MachNode{
    final TypeFunPtr _tfp;
    final String _name;
    CallRISC( CallNode call, TypeFunPtr tfp ) {
        super(call);
        _inputs.pop(); // Pop constant target
        assert tfp.isConstant();
        _tfp = tfp;
        _name = CodeGen.CODE.link(tfp)._name;
    }

    @Override public String label() { return op(); }
    @Override public RegMask regmap(int i) {
        return riscv.callInMaskInt(i); // Normal argument
    }
    @Override public RegMask outregmap() { return riscv.RET_MASK; }

    @Override public String name() { return _name; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(_name);
    }

    @Override public String op() { return "call"; }
}
