package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFunPtr;

public class CallARM extends CallNode implements MachNode, RIPRelSize {
    final TypeFunPtr _tfp;
    final String _name;

    CallARM(CallNode call, TypeFunPtr tfp) {
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
    @Override public RegMask regmap(int i) { return arm.callInMask(_tfp,i,fun()._maxArgSlot); }
    @Override public RegMask outregmap() { return null; }

    @Override public void encoding( Encoding enc ) {
        enc.relo(this);
        // BL
        enc.add4(arm.b(arm.OP_CALL,0)); // Target patched at link time
    }

    // Delta is from opcode start, but X86 measures from the end of the 5-byte encoding
    @Override public byte encSize(int delta) { return 4; }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        enc.patch4(opStart,arm.b(arm.OP_CALL,delta));
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(_name);
        for( int i=0; i<nargs(); i++ )
            sb.p(code.reg(arg(i+2))).p("  ");
        sb.unchar(2);
    }
}
