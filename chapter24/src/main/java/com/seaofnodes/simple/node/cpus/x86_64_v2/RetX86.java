package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class RetX86 extends ReturnNode implements MachNode {
    RetX86( ReturnNode ret, FunNode fun ) { super(ret, fun); fun.setRet(this); }
    @Override public void encoding( Encoding enc ) {
        int sz = fun()._frameAdjust;
        if( sz != 0 ) {
            enc.add1( x86_64_v2.REX_W ).add1( x86_64_v2.imm8(sz) ? 0x83 : 0x81 );
            enc.add1( x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0b000, x86_64_v2.RSP) );
            if( x86_64_v2.imm8(sz) )  enc.add1(sz);
            else                      enc.add4(sz);
        }
        enc.add1(0xC3);
    }
}
