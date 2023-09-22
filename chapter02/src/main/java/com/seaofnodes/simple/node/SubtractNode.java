package com.seaofnodes.simple.node;

public class SubtractNode extends Node {
    public SubtractNode(Node lhs, Node rhs) {
        super(null, lhs, rhs);
    }

    @Override
    public String toString() { return "(" + in(1).toString() + "-" + in(2).toString() + ")"; }

}
