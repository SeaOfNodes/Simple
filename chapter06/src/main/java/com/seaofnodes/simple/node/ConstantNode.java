package com.seaofnodes.simple.node;

import com.seaofnodes.print.ExprPrinter;

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
    final Type _con;
    public ConstantNode( Type type ) {
        super(Parser.START);
        _con = type;
    }

    @Override protected String repeatName() {
        return _con==null || _con.toString().length()<=32 ? null : uniqueName();
    }

    @Override
    public String label() { return "Con"; }


    @Override protected ExprPrinter<Node> _print1(ExprPrinter<Node> p) {
        return p.p(_con);
    }

    @Override
    public Type compute() { return _con; }

    @Override
    public Node idealize() { return null; }
}
