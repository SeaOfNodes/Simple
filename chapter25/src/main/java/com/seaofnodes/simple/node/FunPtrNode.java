package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import java.util.BitSet;

// Upcast (join) the input to a t.  Used after guard test to lift an input.
// Can also be used to make a type-assertion if ctrl is null.
public class FunPtrNode extends Node {
    public FunPtrNode(FunNode fun) { super(new Node[]{fun}); }

    public FunPtrNode(FunPtrNode c) {
        super(c);               // Call parent copy constructor
    }
    @Override public Tag serialTag() { return Tag.FunPtr; }
    static Node make( BAOS bais)  { return new FunPtrNode((FunNode)null); }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append("FunPtr of ");
    }


    @Override
    public Type compute() {
        FunNode fun = (FunNode)in(0);
        return fun.sig();
    }

    @Override
    public Node idealize() {
        return null;
    }

    // Acts like a constant, sometimes.
    @Override public boolean isConst() { return true; }

}
