package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFunPtr;

public class CallRISC extends CallNode implements MachNode, RIPRelSize {
    final TypeFunPtr _tfp;
    final String _name;
    CallRISC( CallNode call, TypeFunPtr tfp ) {
        super(call);
        assert tfp.isConstant();
        _inputs.pop(); // Pop constant target
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
        enc.relo(this);
        short rpc = enc.reg(this);
        //// High half is where the TFP constant used to be, the last input
        //short auipc = enc.reg(in(_inputs._len-1));
        //enc.add4(riscv.i_type(0x67, rpc, 0, auipc, 0));
        // TODO: JAL for range
        throw Utils.TODO();
    }

    // Delta is from opcode start, but X86 measures from the end of the 5-byte encoding
    @Override public byte encSize(int delta) { return 4; }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        short rpc = enc.reg(this);
        // High half is where the TFP constant used to be, the last input
        short auipc = enc.reg(in(_inputs._len-1));
        enc.patch4(opStart,riscv.i_type(0x67, rpc, 0, auipc, delta&0xFFF));
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(_name).p("  ");
        for( int i=0; i<nargs(); i++ )
            sb.p(code.reg(arg(i+2))).p(", ");
        sb.unchar(2).p("  ").p(code.reg(fptr()));

    }
}
