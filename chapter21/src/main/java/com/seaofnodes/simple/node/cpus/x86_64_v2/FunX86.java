package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.FunNode;
import com.seaofnodes.simple.node.MachNode;

public class FunX86 extends FunNode implements MachNode {
    FunX86( FunNode fun ) { super(fun); }
    @Override public String op() { return "subi"; }
    @Override public RegMask regmap(int i) { return null; }
    @Override public RegMask outregmap() { return null; }
    @Override public void postSelect(CodeGen code) { code.link(this);  }

    // Actual stack layout is up to each CPU.
    // X86, with too many args & spills:
    // | CALLER |
    // |  argN  | // slot 1, required by callER
    // +--------+ // Old frame
    // |  RPC   | // slot 0, required by callER
    // +--------+ // New frame top
    // |  PAD16 |
    // | callee | // slot 3, callEE
    // | callee | // slot 2, callEE
    // +--------+
    @Override public void encoding( Encoding enc ) {
        // Stack frame size: _maxSlot - max(arg), padded to 16.
        _maxArgSlot = x86_64_v2.maxArgSlot(enc._fun.sig());
        int sz = _maxSlot - _maxArgSlot;
        // If non-leaf function, pad to 16b
        if( _hasCalls ) sz = ((sz+1) & -2)+1;
        assert x86_64_v2.imm8(sz*8);
        _frameAdjust = (short)sz;
        if( sz == 0 ) return; // Skip if no frame adjust

        // opcode: 0x83, addi rsp with immediate 8
        enc.add1( x86_64_v2.REX_W ).add1( 0x83 );
        enc.add1( x86_64_v2.modrm(x86_64_v2.MOD.DIRECT, 0b101, x86_64_v2.RSP) );
        enc.add1(sz*8);
    }

    @Override public void asm(CodeGen code, SB sb) {
        sb.p("rsp -= #").p(_frameAdjust*8);
    }
}
