package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;

import java.util.Objects;

/**
 * A Constant node represents a constant value.  At present, the only constants
 * that we allow are integer literals; therefore Constants contain an integer
 * value. As we add other types of constants, we will refactor how we represent
 * Constants.
 * <p>
 * Constants have no semantic inputs. However, we set Start as an input to
 * Constants to enable a forward graph walk.  This edge carries no semantic
 * meaning, and it is present <em>solely</em> to allow visitation.
 * <p>
 * The Constant's value is the value stored in it.
 */
public class ConstantNode extends Node {

    public final long _value;

    public ConstantNode(long value, StartNode startNode) {
        super(startNode);
        _value = value;
    }

    @Override
    public Type compute() {
        return new TypeInteger(_value);
    }

    @Override
    public String toString() { return Objects.toString(_value); }

    public String label() {
        return "Constant " + _value;
    }

    @Override
    public String uniqueName() {
        return "Constant" + _nid;
    }
}
