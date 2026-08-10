package com.seaofnodes.simple.node;

import com.seaofnodes.simple.parser.Lexer;
import com.seaofnodes.simple.parser.Parser;
import com.seaofnodes.simple.type.*;

/** Resolve default formatting through normal type flow, including forward
 * references. Valid programs fold this selector before serialization/codegen. */
public final class FormatNode extends Node {
    private final Lexer _site;
    public FormatNode(Node value, Node ints, Node floats, Node strings, Lexer site) {
        super(null,value,ints,floats,strings);
        _site=site;
    }
    @Override public String label() { return "Format"; }
    private int choice() {
        Type t=in(1)._type;
        if( t instanceof TypeInteger ) return 2;
        if( t instanceof TypeFloat ) return 3;
        if( t instanceof TypeMemPtr p && !p.nullable() && p._obj.isAry() &&
            p._obj._name.equals("[]u8") ) return 4;
        return 0;
    }
    @Override public Type compute() {
        if( in(1)._type.isHigh() ) return Type.TOP;
        int c=choice();
        return c==0 ? in(2)._type.meet(in(3)._type).meet(in(4)._type) : in(c)._type;
    }
    @Override public Node idealize() { int c=choice(); return c==0 ? null : in(c); }
    @Override public Parser.ParseException err() {
        return Parser.error("Cannot format "+in(1)._type+"; expected an integer, float, or byte string",_site);
    }
}
