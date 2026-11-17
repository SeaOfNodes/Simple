package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.isa.X86;
import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class RetX86 extends ReturnNode implements MachNode {
    RetX86( ReturnNode ret, FunNode fun ) { super(ret, fun); fun.setRet(this); }
    @Override public void encoding( Encoding enc ) {
        int sz = fun()._frameAdjust;
        if( sz!=0 ) X86.imm(enc,0x81,0,x86_64_v2.RSP,sz);
        X86.ret(enc);
    }
}
