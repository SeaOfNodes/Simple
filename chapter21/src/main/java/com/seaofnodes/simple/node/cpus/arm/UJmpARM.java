package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import java.util.BitSet;

// unconditional jump
public class UJmpARM extends CFGNode implements MachNode {
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
        // TODO: unconditional jump
        int body = arm.b_cond(0b01010100, 0, arm.make_condition("<"));
        enc.add4(body);
    }

    @Override public void asm(CodeGen code, SB sb) {
        CFGNode target = uctrl();
        assert target.nOuts() > 1; // Should optimize jmp to empty targets
        sb.p(label(target));
    }
}
