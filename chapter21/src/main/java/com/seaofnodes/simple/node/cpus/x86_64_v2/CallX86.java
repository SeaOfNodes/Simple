package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.codegen.RegMask;
import com.seaofnodes.simple.node.CallNode;
import com.seaofnodes.simple.node.MachNode;
import com.seaofnodes.simple.type.TypeFunPtr;

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
    @Override public String op() { return "call"; }
    @Override public String label() { return op(); }
    @Override public String name() { return _name; }
    @Override public TypeFunPtr tfp() { return _tfp; }
    @Override public RegMask regmap(int i) { return x86_64_v2.callInMask(_tfp,i);  }
    @Override public RegMask outregmap() { return null; }
    @Override public int nargs() { return nIns()-2; } // Minus control, memory, fptr

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        enc.relo(this,_tfp);
        enc.add1(0xe8);
        enc.add4(0);            // Target patched at link time
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(_name).p("  ");
        for( int i=0; i<nargs(); i++ )
            sb.p(code.reg(in(i+2))).p("  ");
        sb.unchar(2);
    }
}
