package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import java.util.BitSet;

// unconditional jump
public class UJmpARM extends CFGNode implements MachNode, RIPRelSize {
    UJmpARM() { }
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
        int body = arm.b(arm.OP_UJMP, 0);
        enc.add4(body);
    }

    // Delta is from opcode start
    @Override public byte encSize(int delta) {
        if( -(1<<26) <= delta && delta < (1<<26) ) return 4;
        // 2 word encoding needs a tmp register, must teach RA
        throw Utils.TODO();
    }

    // Delta is from opcode start
    @Override public void patch( Encoding enc, int opStart, int opLen, int delta ) {
        if( opLen==4 ) {
            enc.patch4(opStart,arm.b(arm.OP_UJMP, delta));
        } else {
            throw Utils.TODO();
        }
    }

    @Override public void asm(CodeGen code, SB sb) {
        CFGNode target = uctrl();
        //assert target.nOuts() > 1; // Should optimize jmp to empty targets
        sb.p(label(target));
    }
}
