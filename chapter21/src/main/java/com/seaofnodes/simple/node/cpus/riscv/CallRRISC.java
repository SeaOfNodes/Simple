package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class CallRRISC extends CallNode implements MachNode{
    CallRRISC( CallNode call ) { super(call); }

    @Override public String label() { return op(); }
    @Override public RegMask regmap(int i) {
        // Todo: float or int?
        return i==_inputs._len
            ? riscv.RMASK                // Function call target
            : riscv.callInMask(tfp(),i); // Normal argument
    }
    @Override public RegMask outregmap() { return null; }

    // Encoding is appended into the byte array; size is returned
    @Override public void encoding( Encoding enc ) {
        // combo of:
        //  auipc    ra,0x0
        //  jalr    ra # 0 <main>
        // TODO: relocs
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
        sb.p(code.reg(fptr())).p("  ");
        for( int i=0; i<nargs(); i++ )
            sb.p(code.reg(arg(i))).p("  ");
        sb.unchar(2);
    }

    @Override public String op() { return "callr"; }

}
