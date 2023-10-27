package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeBot;

public class RegionNode extends Node implements Control {
    public RegionNode(Node... inputs) {
        super(inputs);
    }

    @Override
    public String label() {
        return "Region";
    }

    @Override
    StringBuilder _print1(StringBuilder sb) {
        return sb;
    }

    @Override
    public Type compute() {
        return TypeBot.BOTTOM;
    }

    @Override
    public Node idealize() {
        return null;
    }
}
