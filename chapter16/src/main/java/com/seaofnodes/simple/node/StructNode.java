package com.seaofnodes.simple.node;

import com.seaofnodes.simple.type.*;
import java.util.BitSet;

/**
 * Build a compound object
 */
public class StructNode extends Node {

    public TypeStruct _ts;

    @Override public String label() { return _ts==null ? "STRUCT?" : _ts.str(); }

    @Override
    StringBuilder _print1(StringBuilder sb, BitSet visited) {
        if( _ts==null ) return sb.append("STRUCT?");
        sb.append(_ts._name).append(" {");
        for( int i=0; i<nIns(); i++ ) {
            sb.append(_ts._fields[i]._fname).append(":");
            (in(i)==null ? Type.BOTTOM : in(i)._type).print(sb);
            sb.append("; ");
        }
        sb.setLength(sb.length()-2);
        return sb.append("}");
    }

    // All fields fully initialized, or a sample not-initialized field
    public String uninitField() {
        for( int i=0; i<nIns(); i++ )
            if( in(i)==null )
                return _ts._fields[i]._fname;
        return null;
    }

    // If this is a mem struct, then alias inputs are made lazily.
    // If missing, use Start.Mem instead.
    Node alias( int alias ) {
        return in( alias >= nIns() || in(alias)==null ? 1 : alias );
    }

    Node alias( int alias, Node st ) {
        while( alias >= nIns() )
            addDef(null);
        return setDef(alias,st);
    }


    @Override
    public TypeStruct compute() {
        if( _ts==null ) return TypeStruct.BOT;
        Field[] fs = new Field[_ts._fields.length];
        for( int i=0; i<fs.length; i++ )
            fs[i] = _ts._fields[i].makeFrom(in(i)==null ? Type.TOP : in(i)._type);
        return TypeStruct.make(_ts._name,fs);
    }

    @Override
    public Node idealize() { return null; }

    @Override
    boolean eq(Node n) { return _ts == ((StructNode)n)._ts; }

    @Override
    int hash() { return _ts==null ? 0 : _ts.hashCode(); }
}
