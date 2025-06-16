package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMemPtr;
import com.seaofnodes.simple.util.AryInt;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;
import java.lang.StringBuilder;
import java.util.BitSet;
import java.util.HashMap;

/**
 * Convenience common base for Load and Store.
 *
 * 0 - Control
 * 1 - Memory
 * 2 - Base of object, OOP
 * 3 - Offset, integer types
 * 4 - Value, for Stores only
 */
public abstract class MemOpNode extends Node {

    // The equivalence alias class
    public final int _alias;

    // True if load-like, false if store-like.
    //
    // Stores produce memory (maybe as part of a tuple with other things),
    // loads do not.
    //
    // Loads might pick up anti-dependences on prior Stores, and never cause an
    // anti-dependence themselves.
    //
    // Stores must maximally sink to the least dominator use.  Loads can be
    // opportunistically hoisted.
    public final boolean _isLoad;

    // Declared type; not final because it might be a forward-reference
    // which will be lazily improved when the reference is declared.
    public Type _declaredType;

    // A debug name, no semantic meaning
    public final String _name;
    // Source location for late reported errors
    public final Parser.Lexer _loc;

    public MemOpNode(Parser.Lexer loc, String name, int alias, boolean isLoad, Type glb, Node mem, Node ptr, Node off) {
        super(null, mem, ptr, off);
        _name  = name;
        _alias = alias;
        _declaredType = glb;
        _loc = loc;
        _isLoad = isLoad;
    }
    public MemOpNode(Parser.Lexer loc, String name, int alias, boolean isLoad, Type glb, Node mem, Node ptr, Node off, Node value) {
        this(loc,name, alias, isLoad, glb, mem, ptr, off);
        addDef(value);
    }
    public MemOpNode( Node mach, MemOpNode mop ) {
        super(mach);
        _name  = mop==null ? null : mop._name;
        _alias = mop==null ? 0    : mop._alias;
        _loc   = mop==null ? null : mop._loc;
        _isLoad= mop==null ? true : mop._isLoad;
        _declaredType = mop==null ? Type.BOTTOM : mop._declaredType;
        if( mop==null )
            throw Utils.TODO("Load or not");
    }

    // Used by M2 when translating graph to Simple
    public MemOpNode( boolean isLoad ) {
        super((Node)null);
        _name         = null;
        _alias        = 0;
        _loc          = null;
        _isLoad       = isLoad;
        _declaredType = Type.BOTTOM;
    }

    MemOpNode( BAOS bais, String[] strs, Type[] types, AryInt aliases, boolean isLoad ) {
        this(null,
             strs[bais.packed2()],
             aliases.at(bais.packed2()),isLoad,
             types[bais.packed2()],null,null,null);
    }

    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types, HashMap<Integer,Integer> aliases) {
        baos.packed2(_name==null ? 0 : strs.get(_name));
        baos.packed2(aliases.get(_alias));
        baos.packed2(types.get(_declaredType)); // NPE if fails lookup
        assert _isLoad == this instanceof LoadNode; // No machine ops
    }

    static String mlabel(String name) { return "[]"==name ? "ary" : ("#"==name ? "len" : name); }
    String mlabel() { return mlabel(_name); }

    public Node mem() { return in(1); }
    public Node ptr() { return in(2); }
    public Node off() { return in(3); }

    @Override public StringBuilder _print1( StringBuilder sb, BitSet visited ) { return _printMach(sb,visited);  }
    public StringBuilder _printMach( StringBuilder sb, BitSet visited ) { throw Utils.TODO(); }
    public int log_size() { return _declaredType.log_size();  }

    @Override
    public boolean eq(Node n) {
        MemOpNode mem = (MemOpNode)n; // Invariant
        return _alias==mem._alias;    // When comparing types error to use "equals"; always use "=="
    }

    @Override
    int hash() { return _alias; }

    @Override
    public Parser.ParseException err() {
        Type ptr = ptr()._type;
        // Already an error, but better error messages come from elsewhere
        if( ptr == Type.BOTTOM ) return null;
        if( ptr.isHigh() ) return null; // Assume it will fall to not-null
        // Better be a not-nil TMP
        if( ptr instanceof TypeMemPtr tmp && tmp.notNull() )
            return null;
        return Parser.error( "Might be null accessing '" + _name + "'",_loc);
    }
}
