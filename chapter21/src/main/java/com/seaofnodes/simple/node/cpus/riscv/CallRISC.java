package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFunPtr;

public class CallRISC extends CallNode implements MachNode {
    final TypeFunPtr _tfp;
    final String _name;
    CallRISC( CallNode call, TypeFunPtr tfp, AUIPC auipc ) {
        super(call);
        assert tfp.isConstant();
        _inputs.pop(); // Pop constant target
        _inputs.add(auipc);     // Add high-half address
        _tfp = tfp;
        _name = CodeGen.CODE.link(tfp)._name;
    }

    @Override public String op() { return "call"; }
    @Override public String label() { return op(); }
    @Override public RegMask regmap(int i) {
        // Last call input is AUIPC
        if( i == nIns()-1 ) return riscv.RMASK;
        return riscv.callInMask(_tfp,i);
    }
    @Override public RegMask outregmap() { return riscv.RPC_MASK; }
    @Override public String name() { return _name; }
    @Override public TypeFunPtr tfp() { return _tfp; }

    @Override public void encoding( Encoding enc ) {
        enc.relo(this,_tfp);
        short rpc = enc.reg(this);
        // High half is where the TFP constant used to be, the last input
        short auipc = enc.reg(in(_inputs._len-1));
        int body = riscv.i_type(0x67, rpc, 0/*JALR.C*/, auipc, 0);
        enc.add4(body);
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(_name).p("  ");
        for( int i=0; i<nargs(); i++ )
            sb.p(code.reg(arg(i))).p("  ");
        sb.unchar(2);
    }
}
