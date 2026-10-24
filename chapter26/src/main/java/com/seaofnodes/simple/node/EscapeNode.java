package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.*;

/** Publish one initialized field: {control, public memory, private memory, self}.
 *  This is an explicit construction boundary, not escape discovery. The next
 *  chapter will discover which objects and functions unknown code can reach.
 */
public class EscapeNode extends Node {
    public Field _field;
    private final Parser.Lexer _loc;
    public EscapeNode(Parser.Lexer loc, Field field, Node ctrl, Node pub, Node priv, Node self) {
        super(ctrl,pub,priv,self);
        _field=field;
        _loc=loc;
    }
    public EscapeNode(EscapeNode esc) { super(esc); _field=esc._field; _loc=esc._loc; }
    public Node pub() { return in(1); }
    public Node priv() { return in(2); }
    public Node self() { return in(3); }
    @Override public boolean isMem() { return true; }
    @Override public String label() { return "Escape_"+_field._fname; }

    private Type value() {
        return MemMergeNode.contents(priv(),_field._alias,this);
    }
    @Override public Type compute() {
        if( in(0)._type.isHigh() || priv()._type.isHigh() || pub()._type.isHigh() ) return TypeMem.TOP;
        return TypeMem.make(_field._alias,value().meet(MemMergeNode.contents(pub(),_field._alias,this)).join(_field._t));
    }
    @Override public Node idealize() {
        if( priv() instanceof MemMergeNode merge ) {
            setDef(2,merge.alias(_field._alias));
            return this;
        }
        if( pub() instanceof MemMergeNode merge ) {
            setDef(1,merge.alias(_field._alias));
            return this;
        }
        return null;
    }
    @Override public Parser.ParseException err() {
        if( in(0)._type.isHigh() ) return null;
        if( value()==Type.BOTTOM ) {
            String name = self()._type instanceof TypeMemPtr p ? p._obj._name : "Object";
            return Parser.error("'"+name+"' is not fully initialized, field '"+_field._fname+"' needs to be set in a constructor",_loc);
        }
        return null;
    }
    @Override boolean _upgradeType(java.util.HashMap<String,Type> types) {
        Field next = (Field)_field.upgradeType(types);
        if( next==_field ) return false;
        unlock(); _field=next; return true;
    }
    @Override public boolean eq(Node n) { return _field==((EscapeNode)n)._field; }
    @Override int hash() { return _field.hashCode(); }
}
