package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Var;
import com.seaofnodes.simple.type.Type;
import java.util.BitSet;

/**
 *  A Forward Reference.  Its any final constant, including functions.  When
 *  the Def finally appears its plugged into the forward reference, which then
 *  peepholes to the Def.
 */
public class FRefNode extends ConstantNode {
    public static final Type FREF_TYPE = Type.BOTTOM;
    public final String _name, _src;
    public final Parser.Lexer _loc;    // Source location

    public FRefNode( String srcName, String src, Parser.Lexer loc ) { super(FREF_TYPE); _name = name; _src = src; _loc = loc; }

    @Override public String label() { return "FRef"+_name; }

    @Override public String uniqueName() { return "FRef_" + _nid; }

    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append("FRef_").append(_name);
    }

    @Override public Node idealize() {
        // When FRef finds its definition, idealize to it
        return nIns()==1 ? null : in(1);
    }

    public Parser.ParseException err() { return Parser.error("Undefined name '"+_name+"'",_loc); }

    @Override public boolean eq(Node n) { return this==n; }
    @Override int hash() { return _name.hashCode(); }
}
