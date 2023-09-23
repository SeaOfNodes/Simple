package com.seaofnodes.simple.node;

import java.util.Objects;

public class AddNode extends Node {
    public AddNode(Node lhs, Node rhs) {
        super(null, lhs, rhs);
    }

    @Override
    public String toString() { return "(" + in(1).toString() + "+" + in(2).toString() + ")"; }

}
