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
public class EscapeNode extends Node {

    public final int _alias;
    public EscapeNode(int alias, Node self, Node priv, Node pub ) { super(null,self,priv,pub); _alias=alias; }
    public EscapeNode(EscapeNode esc ) {
        super(esc);             // Copy constructor
        // Except we do not want/need self ptr past Opto.
        // *NOTE* the weird direct stomp; this is correct because this
        // constructor does NOT set the backedges, because it is used to
        // copy-construct an entire graph and ALL edges will get rewritten.
        _inputs.set(1,null);
        _alias = esc._alias;
    }

    // Pointer to some private (unescaped) memory from a NewNode
    public Node self() { return in(1); }
    public Node priv() { return in(2); }
    public Node pub () { return in(3); }

    @Override public Tag serialTag() { return Tag.Escape; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        baos.packed1(aliases.get(_alias));
    }
    static Node make( BAOS bais, AryInt aliases)  {
        return new EscapeNode(aliases.at(bais.packed2()), null,null,null);
    }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("Esc#").append(_alias).append(" {");
        if( self()==null ) sb.append("---");
        else self()._print0(sb,visited).append(",");
        priv()._print0(sb,visited).append("}, ");
        pub ()._print0(sb,visited);
        return sb;
    }

    @Override
    public Type compute() {
        // Private memory is the magical alias#1 with a single-field TypeStruct
        if( priv()._type.isHigh() ) return TypeMem.TOP;
        if( pub ()._type.isHigh() ) return TypeMem.TOP;
        TypeMem mpriv = (TypeMem)priv()._type;
        TypeMem mpub  = (TypeMem)pub ()._type;
        assert mpriv._alias==1 || mpriv._alias==_alias;
        assert mpub ._alias==1 || mpub ._alias==_alias;
        Type tpriv;
        if( mpriv._t instanceof TypeStruct ts ) {
            tpriv = ts.at(ts.findAlias(_alias))._t;
        } else {
            tpriv = mpriv._t;
        }
        Type tpub;
        if( mpub._t instanceof TypeStruct ts ) {
            throw Utils.TODO(); // Must be a TypeStruct, peel out field
        } else {
            tpub = mpub._t;
        }

        return TypeMem.make(_alias,tpriv.meet(tpub));
    }

    @Override public Node idealize() {
        if( priv() instanceof MemMergeNode mmm ) {
            setDef(2,mmm.alias(_alias));
            return this;
        }
        if( pub() instanceof MemMergeNode mmm ) {
            setDef(3,mmm.alias(_alias));
            return this;
        }
        return null;
    }

    @Override public boolean eq(Node n) { return _alias==((EscapeNode)n)._alias; }

    @Override int hash() { return _alias; }
}
