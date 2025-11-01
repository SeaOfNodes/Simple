package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import java.util.BitSet;
import java.util.HashMap;

/**
 * Build a compound object
 */
public class EscapeNode extends Node implements MultiNode {

    public final TypeStruct _ts;
    public EscapeNode(TypeStruct ts, Node self, Node selfMem ) { super(self,selfMem); _ts=ts; }

    // Pointer to some private (unescaped) memory from a NewNode
    Node self() { return in(0); }
    Node selfMem() { return in(1); }

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
        sb.append("Escape ");
        sb.append(_ts._name).append(" {  ");
        for( int i=0; i<_ts._fields.length; i++ ) {
            sb.append(_ts._fields[i]._fname).append(":");
            sb.append(in(i+2)._type);
            sb.append("; ");
        }
        sb.setLength(sb.length()-2);
        return sb.append("}");
    }

    @Override
    public Type compute() {

        Type t = selfMem()._type;
        if( t.isHigh() ) return Type.TOP;
        TypeMem allSelfMem = (TypeMem)t;
        TypeStruct allSelf = (TypeStruct)allSelfMem._t;

        Type[] ts = new Type[_ts._fields.length];
        for( int i=0; i<_ts._fields.length; i++ ) {
            Type  pubMem = in(i+2)._type;
            Type selfMem = TypeMem.make(allSelf._fields[i]._alias, allSelf._fields[i]._t);
            ts[i] = pubMem.meet(selfMem);
        }
        return TypeTuple.make(ts);
    }

    @Override public Node idealize() { return null; }

    @Override public boolean eq(Node n) { return _ts == ((EscapeNode)n)._ts; }

    @Override int hash() { return _ts.hashCode(); }
}
