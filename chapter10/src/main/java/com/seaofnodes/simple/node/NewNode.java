package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMemPtr;

import java.util.BitSet;

public class NewNode extends Node {

    TypeMemPtr _ptr;

    public NewNode(TypeMemPtr ptr) {
        super((Node) null);
        this._ptr = ptr;
    }

    public TypeMemPtr ptr() { return _ptr; }

    @Override
    public String label() {
        return "New";
    }

    @Override
    public String glabel() {
        return "New " + ptr();
    }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb;
    }

    @Override
    public Type compute() {
        return _ptr;
    }

    @Override
    public Node idealize() {
        return null;
    }
}
