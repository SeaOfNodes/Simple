package com.seaofnodes.simple.node;

public class UnaryMinusNode extends Node {
    public UnaryMinusNode(Node in) {
        super(null, in);
    }

    @Override
    public String toString() { return "(-" + in(1).toString() + ")"; }
}
