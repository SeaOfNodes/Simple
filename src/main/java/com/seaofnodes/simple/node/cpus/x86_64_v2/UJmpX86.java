package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import java.util.BitSet;

// unconditional jump
public class UJmpX86 extends CFGNode implements MachNode, RIPRelSize {
    UJmpX86() { }
    @Override public String op() { return "jmp"; }
    @Override public String label() { return op(); }
    @Override public StringBuilder _print1( StringBuilder sb, BitSet visited ) {
        return sb.append("jmp ");
    }
    @Override public RegMask regmap(int i) {return null; }
    @Override public RegMask outregmap() { return null; }
    @Override public Type compute() { throw Utils.TODO(); }
    @Override public Node idealize() { throw Utils.TODO(); }
    @Override public void encoding( Encoding enc ) {
        enc.jump(this,uctrl());
        // Short-form jump
        enc.add1(0xEB).add1(0);
        //// E9 cd	JMP rel32
        //enc.add1(0xE9).add4(0);
    }

    // Delta is from opcode start, but X86 measures from the end of the 2-byte encoding
    @Override public byte encSize(int delta) { return (byte)(x86_64_v2.imm8(delta-2) ? 2 : 5); }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        byte[] bits = enc.bits();
        if( opLen==2 ) {
            assert bits[opStart] == (byte)0xEB;
            delta -= 2;         // Offset from opcode END
            assert (byte)delta==delta;
            bits[opStart+1] = (byte)delta;
        } else {
            assert bits[opStart] == (byte)0xEB;
            bits[opStart] = (byte)0xE9; // Long form
            delta -= 5;         // Offset from opcode END
            enc.patch4(opStart+1,delta);
        }
    }

    @Override public void asm(CodeGen code, SB sb) {
        CFGNode target = uctrl();
        //assert target.nOuts() > 1; // Should optimize jmp to empty targets
        sb.p(label(target));
    }
}
