package com.seaofnodes.simple.node.cpus.arm;

import com.seaofnodes.simple.node.CallEndNode;
import com.seaofnodes.simple.node.MachNode;

public class CallEndARM extends CallEndNode implements MachNode {
    CallEndARM( CallEndNode cend ) { super(cend); }
}
