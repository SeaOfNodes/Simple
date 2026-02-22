package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeStruct;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;
import java.util.HashMap;

/**
 * A field offset.  This becomes a constant *after* other optimizations which
 * determine field sizes - some fields might be dead, or hold only zeros or
 * small values.
 */

public class ConFldOffNode extends ConstantNode {
    public TypeStruct _ts;      // Struct holding field
    public final String _fname; // Field name
    public ConFldOffNode( TypeStruct ts, String fname ) { super(TypeInteger.BOT); _ts = ts; _fname = fname; }
    @Override public Tag serialTag() { return Tag.ConFldOff; }

    @Override public String label() {
        return _fname == " len" ? "sizeof("+_ts._name+")" : "#"+_fname;
    }
    @Override public String glabel() { return label(); }
    @Override public String uniqueName() { return "Off_" + _nid; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append(label());
    }

    // Convert field offset to an integer
    public Node asOffset() {
        int fldx = _fname==" len" ? _ts._fields.length : _ts.find(_fname);
        return new ConstantNode(TypeInteger.constant(_ts.offset(fldx))).peephole();
    }

    // Upgrade the internal type
    @Override boolean _upgradeType( HashMap<String,Type> TYPES) {
        boolean progress = super._upgradeType(TYPES);
        TypeStruct ts = (TypeStruct)_ts.upgradeType(TYPES);
        if( ts != _ts ) return progress;
        _ts = ts;
        return true;
    }

    @Override public boolean eq(Node n) {
        ConFldOffNode off = (ConFldOffNode)n; // Invariant
        return _ts.equals(off._ts) && _fname.equals(off._fname) && super.eq(n);
    }
    @Override int hash() { return super.hash() * _ts.hashCode() * _fname.hashCode(); }
}
