package com.seaofnodes.simple.node;

public class MultiplyNode extends Node {
    public MultiplyNode(Node lhs, Node rhs) {
        super(null, lhs, rhs);
    }

    @Override
    public String toString() { return "(" + in(1).toString() + "*" + in(2).toString() + ")"; }

    @Override
    public String label() {
        return "Mul";
    }
}
