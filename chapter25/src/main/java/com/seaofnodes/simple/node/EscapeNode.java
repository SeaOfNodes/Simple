package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.*;
import java.util.BitSet;
import java.util.HashMap;

/**
 *  Merge public and private aliases.
 *  0 - control
 *  1 - private pointer
 *  2 - private alias
 *  3 - public alias
 */
public class EscapeNode extends TypeNode {

    public EscapeNode(Field fld, Node self, Node priv, Node pub ) { super(fld,null,self,priv,pub); }
    public EscapeNode(EscapeNode esc ) {
        super(esc);             // Copy constructor
        // Except we do not want/need self ptr past Opto.
        // *NOTE* the weird direct stomp; this is correct because this
        // constructor does NOT set the backedges, because it is used to
        // copy-construct an entire graph and ALL edges will get rewritten.
        _inputs.set(1,null);
        _con = esc._con;
    }

    // Pointer to some private (unescaped) memory from a NewNode
    public Node self() { return in(1); }
    public Node priv() { return in(2); }
    public Node pub () { return in(3); }
    public Field fld() { return (Field)_con; }

    @Override public Tag serialTag() { return Tag.Escape; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        baos.packed2(types.get(_con));
    }
    static Node make( BAOS bais, Type[] types)  {
        return new EscapeNode((Field)types[bais.packed2()], null,null,null);
    }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("Esc#").append(fld()._alias).append(" {");
        if( self()==null ) sb.append("---");
        else self()._print0(sb,visited).append(",");
        priv()._print0(sb,visited).append("}, ");
        pub ()._print0(sb,visited);
        return sb;
    }

    @Override
    public Type compute() {
        if( priv()._type.isHigh() ) return TypeMem.TOP;
        if( pub ()._type.isHigh() ) return TypeMem.TOP;
        TypeMem mpriv = (TypeMem)priv()._type;
        TypeMem mpub  = (TypeMem)pub ()._type;
        // Private memory comes from an allocation or other non-aliased source.
        // Public  memory comes from anywhere, might be bulk memory with a very weak mem type.
        assert mpriv._alias==1 || mpriv._alias==fld()._alias;
        assert mpub ._alias==1 || mpub ._alias==fld()._alias;
        Type tpriv = mpriv._t instanceof TypeStruct ts
            ? ts.at(ts.findAlias(fld()._alias))._t
            : mpriv._t;
        assert !(mpub._t instanceof TypeStruct); // Never expect this, but if it starts to happen, need to optimize
        Type tpub = mpub._t;

        return TypeMem.make(fld()._alias, tpriv.meet(tpub).join(fld()._t), false, fld()._final );
    }

    @Override public Node idealize() {
        if( priv() instanceof MemMergeNode mmm ) {
            setDef(2,mmm.alias(fld()._alias));
            return this;
        }
        if( pub() instanceof MemMergeNode mmm ) {
            setDef(3,mmm.alias(fld()._alias));
            return this;
        }
        return null;
    }

//// Upgrade the internal type
//@Override boolean _upgradeType( HashMap<String,Type> TYPES) {
//    Type t = _con.upgradeType(TYPES);
//    if( t == _con ) return false;
//    unlock();               // Unlock before changing _con
//    _con = t;
//    return true;
//}
//
    @Override public boolean eq(Node n) {
        if( fld()._alias!=((EscapeNode)n).fld()._alias )
            return false;
        assert _con == ((EscapeNode)n)._con;
        return true;
    }

    @Override int hash() { return _con.hashCode(); }
}
