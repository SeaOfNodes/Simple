package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.util.SB;

import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;

/**
    A constant with external linkage.
 */

public class ExternNode extends ConstantNode {
    public final String _extern;
    // An exact external function identity has no local FunNode/Return to own.
    // Its lifetime and eventual relocation are carried by the extern symbol
    // and CodeGen's FIDX-to-extern mapping instead.
    public ExternNode(Type t, String ex) { super(t,true); _extern = ex; }


    @Override public String  label() { return "#"+_con+":"+_extern; }

    @Override public boolean eq(Node n) { return this==n; }


}
