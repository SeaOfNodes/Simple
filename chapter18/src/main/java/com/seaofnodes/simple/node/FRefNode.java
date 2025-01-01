package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeFunPtr;
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
public class FRefNode extends ConstantNode {
    public final ScopeMinNode.Var _n;
    public FRefNode(ScopeMinNode.Var n) { super(TypeFunPtr.BOT); _n = n; }

    @Override
    public String label() { return "FRef"+_n; }

    @Override
    public String uniqueName() { return "FRef_" + _nid; }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append("FRef_").append(_n);
    }

    @Override public Node idealize() {
        // When FRef finds its definition, idealize to it
        return nIns()==1 ? null : in(1);
    }

    public Parser.ParseException err() { return Parser.error("Undefined name '"+_n._name+"'",_n._loc); }

    @Override boolean eq(Node n) { return this==n; }
    @Override int hash() { return _n._idx; }
}
