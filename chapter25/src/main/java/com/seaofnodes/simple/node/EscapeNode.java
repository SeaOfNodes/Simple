package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
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
    public EscapeNode(EscapeNode esc ) { super(null,null,esc.priv(),esc.pub()); _alias=esc._alias; }

    // Pointer to some private (unescaped) memory from a NewNode
    Node self() { return in(1); }
    Node priv() { return in(2); }
    Node pub () { return in(3); }

    @Override public Tag serialTag() { return Tag.Escape; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        //baos.packed1(nIns());
        //baos.packed2(_label==null ? 0 : strs.get(_label));
        //baos.packed2(types.get(_type)); // Write _type not _minType, which can be higher
        throw Utils.TODO();
    }
    static Node make( BAOS bais, Type[] types)  {
        //Node[] ins = new Node[bais.packed1()];
        //return new EscapeNode(types[bais.packed2()], ins);
        throw Utils.TODO();
    }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        sb.append("Esc#").append(_alias).append(" {");
        self()._print0(sb,visited).append(",");
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
