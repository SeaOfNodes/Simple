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
public class StructNode extends Node {

    public final TypeStruct _ts;
    public StructNode(TypeStruct ts) { _ts=ts; assert !ts._open; }
    @Override public Tag serialTag() { return Tag.Struct; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        //baos.packed1(nIns());
        //baos.packed2(_label==null ? 0 : strs.get(_label));
        //baos.packed2(types.get(_type)); // Write _type not _minType, which can be higher
        throw Utils.TODO();
    }
    static Node make( BAOS bais, Type[] types)  {
        //Node[] ins = new Node[bais.packed1()];
        //return new StructNode(types[bais.packed2()], ins);
        throw Utils.TODO();
    }

    @Override
    public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        if( _ts==null ) return sb.append("STRUCT?");
        sb.append(_ts._name).append(" {");
        for( int i=0; i<nIns(); i++ ) {
            sb.append(_ts._fields[i]._fname).append(":");
            sb.append(in(i)==null ? Type.BOTTOM : in(i)._type);
            sb.append("; ");
        }
        sb.setLength(sb.length()-2);
        return sb.append("}");
    }

    @Override
    public TypeStruct compute() {
        if( _ts==null ) return TypeStruct.BOT;
        Field[] fs = new Field[_ts._fields.length];
        for( int i=0; i<fs.length; i++ )
            fs[i] = _ts._fields[i].makeFrom(in(i)==null ? Type.TOP : in(i)._type);
        return TypeStruct.make(_ts._name,false,fs);
    }

    @Override
    public Node idealize() { return null; }

    @Override
    public boolean eq(Node n) { return _ts == ((StructNode)n)._ts; }

    @Override
    int hash() { return _ts==null ? 0 : _ts.hashCode(); }
}
