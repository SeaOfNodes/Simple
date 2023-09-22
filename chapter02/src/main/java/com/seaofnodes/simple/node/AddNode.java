package com.seaofnodes.simple.node;

import java.util.Objects;

public class AddNode extends Node {
    public AddNode(Node in1, Node in2) {
        super(null, in1, in2);
    }

    @Override
    public String toString() { return "(" + in(1).toString() + "+" + in(2).toString() + ")"; }

}
