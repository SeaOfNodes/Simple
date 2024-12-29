package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.type.Type;

import java.util.BitSet;

/**
 * A Constant node represents a constant value.  At present, the only constants
 * that we allow are integer literals; therefore Constants contain an integer
 * value.  As we add other types of constants, we will refactor how we represent
 * Constants.
 * <p>
 * Constants have no semantic inputs. However, we set Start as an input to
 * Constants to enable a forward graph walk.  This edge carries no semantic
 * meaning, and it is present <em>solely</em> to allow visitation.
 * <p>
 * The Constant's value is the value stored in it.
 */
public class ConstantNode extends Node {
    public final Type _con;
    public ConstantNode( Type type ) {
        super(Parser.START);
        _con = type;
    }

    public static Node make( Type type ) {
        if( type==Type. CONTROL ) return new CtrlNode();
        if( type==Type.XCONTROL ) return new XCtrlNode();
        return new ConstantNode(type);
    }

    @Override
    public String label() { return "#"+_con; }

    @Override
    public String uniqueName() { return "Con_" + _nid; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append(_con.print(new SB()));
    }

    @Override public boolean isMultiTail() { return true; }
    @Override public boolean isConst() { return true; }

    @Override
    public Type compute() { return _con; }

    @Override
    public Node idealize() { return null; }

    @Override
    boolean eq(Node n) {
        ConstantNode con = (ConstantNode)n; // Contract
        return _con==con._con;
    }

    @Override
    int hash() { return _con.hashCode(); }
}
