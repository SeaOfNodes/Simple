package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;

public class RetX86 extends ReturnNode implements MachNode {
    RetX86( ReturnNode ret, FunNode fun ) { super(ret, fun); fun.setRet(this); }
    @Override public String op() {
        return ((FunX86)fun())._frameAdjust > 0 ? "addi" : "ret";
    }
    // Correct Nodes outside the normal edges
    @Override public void postSelect(CodeGen code) {
        FunNode fun = (FunNode)rpc().in(0);
        _fun = fun;
        fun.setRet(this);
    }
    @Override public RegMask regmap(int i) { return x86_64_v2.retMask(_fun.sig(),i); }
    @Override public RegMask outregmap() { return null; }
    @Override public void encoding( Encoding enc ) {
        int sz = ((FunX86)fun())._frameAdjust;
        if( sz != 0 ) {
            enc.add1( x86_64_v2.REX_W ).add1( x86_64_v2.imm8(sz*8) ? 0x83 : 0x81 );
            enc.add1( x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0b000, x86_64_v2.RSP) );
            if( x86_64_v2.imm8(sz*8) )  enc.add1(sz*8);
            else                        enc.add4(sz*8);
        }
        enc.add1(0xC3);
    }

    @Override public void asm(CodeGen code, SB sb) {
        int frameAdjust = ((FunX86)fun())._frameAdjust;
        if( frameAdjust>0 )
            sb.p("rsp += #").p(frameAdjust*8).p("\nret");
        // Post code-gen, just print the "ret"
        if( code._phase.ordinal() <= CodeGen.Phase.RegAlloc.ordinal() )
            // Prints return reg (either RAX or XMM0), RPC (always [rsp-4]) and
            // then the callee-save registers.
            for( int i=2; i<nIns(); i++ )
                sb.p(code.reg(in(i))).p("  ");
    }
}
