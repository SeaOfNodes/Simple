package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.CallNode;
import com.seaofnodes.simple.node.MachNode;

public class CallRX86 extends CallNode implements MachNode {
    CallRX86( CallNode call ) { super(call); }
    @Override public String op() { return "callr"; }
    @Override public String label() { return op(); }
    @Override public RegMask regmap(int i) {
        return i==_inputs._len
            ? x86_64_v2.WMASK          // Function call target
            : x86_64_v2.callInMask(tfp(),i,fun()._maxArgSlot); // Normal argument
    }
    @Override public RegMask outregmap() { return null; }
    @Override public void encoding( Encoding enc ) {
        // FF /2	CALL r/m64
        // calls the function in the register
        short src = enc.reg(fptr());
        enc.add1(0xFF);
        enc.add1(x86_64_v2.modrm(x86_64_v2.MOD.INDIRECT,2,src));
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p(code.reg(fptr())).p("  ");
        for( int i=0; i<nargs(); i++ )
            sb.p(code.reg(arg(i))).p("  ");
        sb.unchar(2);
    }

}
