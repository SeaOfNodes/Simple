package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Ary;
import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.CFGNode;

// Interference Graph
abstract public class IFG {
    public static boolean build(int round, Ary<CFGNode> cfg) {
        throw Utils.TODO();
    }
    public static boolean color(int round, CodeGen code) {
        throw Utils.TODO();
    }
}
