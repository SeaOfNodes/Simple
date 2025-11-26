package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeStruct;
import com.seaofnodes.simple.util.SB;
import java.util.BitSet;

/**
 * A field offset.  This becomes a constant *after* other optimizations which
 * determine field sizes - some fields might be dead, or hold only zeros or
 * small values.
 */

public class ConFldOffNode extends ConstantNode {
    public final String _name;  // Struct name
    public final String _fname; // Field name
    public ConFldOffNode( String name, String fname ) { super(TypeInteger.BOT); _name = name; _fname = fname; }

    @Override public String label() {
        return _fname == " len" ? "sizeof("+_name+")" : "#"+_fname;
    }
    @Override public String glabel() { return label(); }
    @Override public String uniqueName() { return "Off_" + _nid; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append(label());
    }

    // Convert field offset to an integer
    public Node asOffset(TypeStruct ts) {
        return new ConstantNode(TypeInteger.constant(ts.offset(_fname==" len" ? ts._fields.length : ts.find(_fname)))).peephole();
    }


    @Override public boolean eq(Node n) {
        ConFldOffNode off = (ConFldOffNode)n; // Invariant
        return _name.equals(off._name) && _fname.equals(off._fname) && super.eq(n);
    }
    @Override int hash() { return super.hash() * _name.hashCode() * _fname.hashCode(); }
}
