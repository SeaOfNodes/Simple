package com.seaofnodes.simple.node;

/**
 * The Start node represents the start of the function.  For now, we do not have any inputs to Start because our function does not
 * yet accept parameters.  When we add parameters the value of Start will be a tuple, and will require Projections to extract the values.
 * We discuss this in detail in Chapter 9: Functions and Calls.
 */
public class StartNode extends Node implements Control {

    public StartNode(/*arguments go here*/) {
        super();
    }

    @Override
    public String label() {
        return "Start";
    }
}
