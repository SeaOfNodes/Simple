package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.RegMask;
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
        return riscv.callInMask(_tfp,i); // Normal argument
    }
    @Override public RegMask outregmap() { return null; }

    @Override public String name() { return _name; }
    @Override public TypeFunPtr tfp() { return _tfp; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        throw Utils.TODO();
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(_name).p("  ");
        for( int i=0; i<nargs(); i++ )
            sb.p(code.reg(arg(i))).p("  ");
        sb.unchar(2);
    }

    @Override public String op() { return "call"; }
}
