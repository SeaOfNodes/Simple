package com.seaofnodes.simple.node.cpus.riscv;

import com.seaofnodes.simple.node.CallEndNode;
import com.seaofnodes.simple.node.MachNode;

public class CallEndRISC extends CallEndNode implements MachNode {
    CallEndRISC( CallEndNode cend ) { super(cend); }
}
