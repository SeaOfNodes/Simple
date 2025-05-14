package com.seaofnodes.simple.node;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.SB;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeStruct;
import java.util.BitSet;

/**
 * A field offset.  This becomes a constant *after* other optimizations which
 * determine field sizes - some fields might be dead, or hold only zeros or
 * small values.
 */

public class ConFldOffNode extends ConstantNode {
    public TypeStruct _obj;     // Struct; may change as parser parses fields
    public final int _fidx;     // Field index
    public ConFldOffNode( TypeStruct obj, int fidx ) { super(TypeInteger.BOT); _obj = obj; _fidx = fidx; }

    @Override public String  label() {
        return _fidx == _obj._fields.length ? "sizeof("+_obj._name+")" : "#"+_obj._fields[_fidx]._fname;
    }
    @Override public String glabel() { return label(); }
    @Override public String uniqueName() { return "Off_" + _nid; }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append(label());
    }

    // Convert field offset to an integer
    public Node asOffset(TypeStruct ts) {
        return new ConstantNode(TypeInteger.constant((_obj=ts).offset(_fidx))).peephole();
    }


    @Override public boolean eq(Node n) {
        ConFldOffNode off = (ConFldOffNode)n; // Invariant
        return _obj==off._obj && _fidx==off._fidx && super.eq(n);
    }
    @Override int hash() { return super.hash()*(_fidx==0 ? -1 : _fidx)^_obj.hashCode(); }
}
