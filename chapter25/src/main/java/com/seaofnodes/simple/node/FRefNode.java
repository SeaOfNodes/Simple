package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Var;
import com.seaofnodes.simple.type.Type;
import java.util.BitSet;

/**
 *  A Forward Reference.  Its any final constant, including functions.  When
 *  the Def finally appears its plugged into the forward reference, which then
 *  peepholes to the Def.

 *  FRefs can be external references, to other Simple defined values in ELF
 *  files, or external FFI/C libs.  If external, a file system lookup needs to
 *  find it in another file, and the name (one time) change to match the found
 *  location.
 *
 *  Example, a ambiguous name "foo" is looked up externally; it might resolve
 *  to "A.B.foo" or "libc.foo" or the like; the name is changed to match.
 */
public class FRefNode extends ConstantNode {
    public static final Type FREF_TYPE = Type.BOTTOM;
    public String _name;
    public final Parser.Lexer _loc;    // Source location

    public FRefNode( String name, Parser.Lexer loc ) { super(FREF_TYPE); _name = name; _loc = loc; }

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
