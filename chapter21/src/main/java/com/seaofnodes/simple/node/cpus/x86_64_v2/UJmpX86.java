package com.seaofnodes.simple.node.cpus.x86_64_v2;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.Type;
import java.util.BitSet;

// unconditional jump
public class UJmpX86 extends CFGNode implements MachNode {
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
        // E9 cd	JMP rel32
        enc.add1(0xE9).add4(0);
    }

    @Override public void asm(CodeGen code, SB sb) {
        CFGNode target = uctrl();
        assert target.nOuts() > 1; // Should optimize jmp to empty targets
        sb.p(label(target));
    }
}
