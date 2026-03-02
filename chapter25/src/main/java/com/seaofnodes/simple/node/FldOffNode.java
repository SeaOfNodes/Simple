package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;
import java.util.HashMap;

/**
 * A field offset.  This becomes a constant *after* other optimizations which
 * determine field sizes - some fields might be dead, or hold only zeros or
 * small values.
 */

public class FldOffNode extends TypeNode {
    public final String _fname; // Field name
    public final Parser.Lexer _loc;   //
    public FldOffNode( Node base, String fname, Parser.Lexer loc ) { super(TypeInteger.BOT,base); _fname = fname; _loc=loc; }
    @Override public Tag serialTag() { throw Utils.TODO("should not reach here"); }

    @Override public String label() {
        return _fname == " len" ? "sizeof()" : "#"+_fname;
    }
    @Override public String glabel() { return label(); }
    @Override public String uniqueName() { return "Off_" + _nid; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append(label());
    }

    @Override public Type compute() {
        return Type.BOTTOM;
    }

    @Override public Node idealize() {
        // When base() gets typed, this xforms into a ConFldOffNode
        if( in(0)._type instanceof TypeMemPtr tmp )
            return new ConFldOffNode(tmp._obj,_fname);
        return null;
    }

    @Override public boolean eq(Node n) {
        FldOffNode off = (FldOffNode)n; // Invariant
        return _fname.equals(off._fname) && super.eq(n);
    }
    @Override int hash() { return super.hash() * _fname.hashCode(); }
}
