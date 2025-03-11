package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.SplitNode;

public class SplitRISC extends SplitNode {
    SplitRISC( String kind, byte round ) { super(kind,round, new Node[2]); }
    @Override public RegMask regmap(int i) { return riscv.SPLIT_MASK; }
    @Override public RegMask outregmap() { return riscv.SPLIT_MASK; }
    @Override public void encoding( Encoding enc ) {
        short dst  = enc.reg(n);
        short src1 = enc.reg(n.in(1));
        int body = r_type(opcode,dst,0,src1,riscv.ZERO,0);
        enc.add4(body);
    }
}
