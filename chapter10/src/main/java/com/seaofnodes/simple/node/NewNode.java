package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMemPtr;

import java.util.BitSet;

public class NewNode extends Node {

    TypeMemPtr _ptr;

    public NewNode(TypeMemPtr ptr, Node ctrl) {
        super(ctrl);
        this._ptr = ptr;
    }

    @Override
    public String label() {
        return "new";
    }

    @Override
    public String glabel() {
        return "new " + _ptr._obj._name;
    }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("new ");
        return _ptr._print(sb);
    }

    @Override
    public Type compute() {
        return _ptr;
    }

    @Override
    public Node idealize() {
        return null;
    }

    @Override
    boolean eq(Node n) { return this == n; }

    @Override
    int hash() { return System.identityHashCode(this); }
}
