package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;

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

    public ConstantNode( Type type ) {
        super(Parser.START);
        _type = type;
    }
    
    @Override
    public String label() { return "Con" + _type; }

    @Override
    StringBuilder _print(StringBuilder sb) {
        return _type._print(sb);
    }
    
    @Override
    public Type compute() { return _type; }

    @Override
    public Node idealize() { return null; }
    
}
