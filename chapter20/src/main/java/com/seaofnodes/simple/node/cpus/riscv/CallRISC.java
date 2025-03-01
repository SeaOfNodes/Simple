package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.LRG;
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
    @Override public RegMask outregmap() { return riscv.RPC_MASK; }

    @Override public String name() { return _name; }
    @Override public TypeFunPtr tfp() { return _tfp; }

    // Encoding is appended into the byte array; size is returned
    @Override public int encoding(ByteArrayOutputStream bytes) {
        // combo of:
        //  auipc    ra,0x0
        //  jalr    ra # 0 <main>
        LRG call_self = CodeGen.CODE._regAlloc.lrg(this);
        short rd = call_self.get_reg();

        int beforeSize = bytes.size();
        //  auipc    ra,0x0
        int body = riscv.u_type(0x17, rd, 0);
        int body2 = riscv.i_type(0x67, rd, 0, rd, 0);
        riscv.push_4_bytes(body, bytes);
        riscv.push_4_bytes(body2, bytes);

        return bytes.size() - beforeSize;
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(_name).p("  ");
        for( int i=0; i<nargs(); i++ )
            sb.p(code.reg(arg(i))).p("  ");
        sb.unchar(2);
    }

    @Override public String op() { return "call"; }
}
