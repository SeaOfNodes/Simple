package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.TypeFunPtr;

// A function address keeps its body alive, even without a linked Call.
public class FunPtrNode extends ConstantNode {
    public FunPtrNode(TypeFunPtr tfp, ReturnNode ret) {
        super(tfp);
        addDef(ret);
    }
    public ReturnNode ret() { return (ReturnNode)in(1); }
}
