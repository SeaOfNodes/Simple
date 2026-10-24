package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;

/** A field name whose receiver's shape is not known while parsing. */
public class FldOffNode extends Node {
    public final String _name;
    public FldOffNode(Node ptr, String name) { super(null,ptr); _name=name; }
    @Override public String label() { return "#"+_name; }
    @Override public Type compute() { return TypeInteger.BOT; }
    @Override public Node idealize() {
        if( in(1)._type instanceof TypeMemPtr ptr && !ptr._obj._open ) {
            if( _name.equals("<<") && ptr._obj.isAry() ) return con(ptr._obj.aryScale());
            if( ptr._obj.field(_name)!=null ) return new ConFldOffNode(ptr._obj._name,_name);
        }
        return null;
    }
    @Override public Parser.ParseException err() {
        return Parser.error("Cannot resolve field '"+_name+"'",null);
    }
    @Override public boolean eq(Node n) { return _name.equals(((FldOffNode)n)._name); }
    @Override int hash() { return _name.hashCode(); }
}
