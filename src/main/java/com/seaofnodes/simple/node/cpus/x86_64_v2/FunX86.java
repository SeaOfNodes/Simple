package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.Encoding;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.MachNode;

public class FunX86 extends FunNode implements MachNode {
    FunX86( FunNode fun ) { super(fun); }
    @Override public void postSelect(CodeGen code) {
        super.postSelect(code);
        // One more for RPC pre-pushed; X86 special
        _maxArgSlot++;
    }

    @Override public void computeFrameAdjust(CodeGen code, int maxReg) {
        super.computeFrameAdjust(code,maxReg);
        // Alignment, but off by RPC
        if( _hasCalls )         // If non-leaf, pad to 16b
            _frameAdjust = ((_frameAdjust+8+8) & -16)-8;
    }

    @Override public void encoding( Encoding enc ) {
        int sz = _frameAdjust;
        if( sz == 0 ) return; // Skip if no frame adjust
        // opcode: 0x83, addi rsp with immediate 8
        enc.add1( x86_64_v2.REX_W ).add1( x86_64_v2.imm8(sz) ? 0x83 : 0x81 );
        enc.add1( x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0b101, x86_64_v2.RSP) );
        if( x86_64_v2.imm8(sz) )  enc.add1(sz);
        else                      enc.add4(sz);
    }

}
