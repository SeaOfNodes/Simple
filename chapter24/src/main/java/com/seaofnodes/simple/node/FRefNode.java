package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.Var;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import java.util.BitSet;
import java.util.HashMap;

/**
 *  A Forward Reference.  Its any final constant, including functions.  When
 *  the Def finally appears its plugged into the forward reference, which then
 *  peepholes to the Def.
 */
public class FRefNode extends Node {
    public final String _name;
    public FRefNode( String name, Type t ) { super(new Node[]{CodeGen.CODE._start});  _name = name;  _type = t; }
    public FRefNode( String name ) { this( name, TypeFRef.make(name)); }
    @Override public Tag serialTag() { return Tag.FRef; }
    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        baos.packed2(strs .get(_name));
        baos.packed2(types.get(_type));
    }
    static Node make( BAOS bais, String[] strs, Type[] types)  { return new FRefNode(strs[bais.packed2()],types[bais.packed2()]); }

    @Override public String label() { return "FRef"+_name; }

    @Override public String uniqueName() { return "FRef_" + _nid; }

    @Override public StringBuilder _print1(StringBuilder sb, BitSet visited) {
        return sb.append("FRef_").append(_name);
    }

    @Override public Type compute() { return _type; }
    @Override public Node idealize() {
        // When FRef finds its definition, idealize to it
        return nIns()==1 ? null : in(1);
    }

    public Parser.ParseException err() { return Parser.error("Undefined name '"+_name+"'",null); }

    @Override public boolean eq(Node n) { return this==n; }
    @Override int hash() { return _name.hashCode(); }
}
