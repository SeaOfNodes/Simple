package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.node.SplitNode;

public class SplitARM extends SplitNode {
    SplitARM(String kind, byte round) { super(kind,round,new Node[2]);}
    @Override public RegMask regmap(int i) { return arm.SPLIT_MASK; }
    @Override public RegMask outregmap() { return arm.SPLIT_MASK; }

    @Override public void encoding( Encoding enc ) {
        short self = enc.reg(this );
        short reg1 = enc.reg(in(1));
        int body = arm.r_reg(0b10101010, 0, reg1, 0, 31, self);
        enc.add4(body);
    }
}
