package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeFunPtr;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;

public class CallRISC extends CallNode implements MachNode, RIPRelSize {
    final TypeFunPtr _tfp;
    final String _name;
    CallRISC( CallNode call, TypeFunPtr tfp ) {
        super(call);
        assert tfp.isConstant();
        _inputs.pop(); // Pop constant target
        _tfp = tfp;
        FunNode fun = CodeGen.CODE.link(tfp);
        _name = fun==null ? ((ExternNode)call.fptr())._extern : fun._name; // Can be null for extern calls
    }

    @Override public String op() { return "call"; }
    @Override public String label() { return op(); }
    @Override public String name() { return _name; }
    @Override public TypeFunPtr tfp() { return _tfp; }
    @Override public RegMask regmap(int i) { return riscv.callInMask(_tfp,i,fun()._maxArgSlot); }
    @Override public RegMask outregmap() { return riscv.RPC_MASK; }
    @Override public int nargs() { return nIns()-2; } // Minus control, memory, fptr

    @Override public void encoding( Encoding enc ) {
        // Short form +/-4K:  beq r0,r0,imm12
        // Long form:  auipc rX,imm20/32; jal r0,[rX+imm12/32]
        FunNode fun = CodeGen.CODE.link(_tfp);
        if( fun==null ) enc.external(this,_name);
        else enc.relo(this);
        short rpc = enc.reg(this);
        enc.add4(riscv.j_type(riscv.OP_JAL, rpc, 0));
    }

    // Delta is from opcode start
    @Override public byte encSize(int delta) {
        if( -(1L<<20) <= delta && delta < (1L<<20) ) return 4;
        // 2 word encoding needs a tmp register, must teach RA
        throw Utils.TODO();
    }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        short rpc = enc.reg(this);
        if( opLen==4 ) {
            enc.patch4(opStart,riscv.j_type(riscv.OP_JAL, rpc, delta));
        } else {
            throw Utils.TODO();
        }
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(_name).p("  ");
        for( int i=0; i<nargs(); i++ )
            sb.p(code.reg(arg(i+2))).p(", ");
        sb.unchar(2);
    }
}
