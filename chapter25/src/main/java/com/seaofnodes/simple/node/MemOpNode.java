package com.seaofnodes.simple.node;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.GlobalBits;
import com.seaofnodes.simple.type.*;
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
public abstract class MemOpNode extends TypeNode {

    // The equivalence alias class
    public int _alias;

    // True if load-like, false if store-like.
    // Used on CPU-specific combined memory+arithmetic ops.
    //
    // Stores produce memory (maybe as part of a tuple with other things),
    // loads do not.
    //
    // Loads might pick up anti-dependencies on prior Stores, and never cause an
    // anti-dependence themselves.
    //
    // Stores must maximally sink to the least dominator use.  Loads can be
    // opportunistically hoisted.
    public final boolean _isLoad;

    // Field name.
    public final String _name;
    // Source location for late reported errors
    public final Parser.Lexer _loc;

    public MemOpNode(Parser.Lexer loc, String name, int alias, boolean isLoad, Type glb, Node ctrl, Node mem, Node ptr, Node off) {
        super(glb, ctrl, mem, ptr, off);
        _name  = name;
        _alias = alias;
        _loc = loc;
        _isLoad = isLoad;
    }
    public MemOpNode(Parser.Lexer loc, String name, int alias, boolean isLoad, Type glb, Node ctrl, Node mem, Node ptr, Node off, Node value) {
        this(loc, name, alias, isLoad, glb, ctrl, mem, ptr, off);
        addDef(value);
    }
    public MemOpNode( Node ideal, MemOpNode mop ) {
        super(ideal,mop._con);
        _name  = mop._name;
        _alias = mop._alias;
        _loc   = mop._loc;
        _isLoad= mop._isLoad;
    }

    MemOpNode( BAOS bais, String[] strs, Type[] types, GlobalBits fileAliases, GlobalBits aliases, boolean isLoad ) {
        this(null,
             strs[bais.packed2()],
             mapAlias(bais,fileAliases,aliases),isLoad,
             types[bais.packed2()],null,null,null,null);
    }

    private static int mapAlias( BAOS bais, GlobalBits fileAliases, GlobalBits aliases ) {
        int alias = bais.packed2();
        return alias < GlobalBits.RESERVED ? alias : aliases.map(fileAliases,alias);
    }

    @Override public void packed(BAOS baos, HashMap<String,Integer> strs, HashMap<Type,Integer> types ) {
        baos.packed2(_name==null ? 0 : strs.get(_name));
        baos.packed2(_alias);
        baos.packed2(types.get(_con));                // NPE if fails lookup
        assert _isLoad == this instanceof LoadNode; // No machine ops
    }

    static String mlabel(String name) { return "[]"==name ? "ary" : ("#"==name ? "len" : name); }
    String mlabel() { return mlabel(_name); }

    public Node mem() { return in(1); }
    public Node ptr() { return in(2); }
    public Node off() { return in(3); }

    @Override public StringBuilder _print1( StringBuilder sb, BitSet visited ) { return _printMach(sb,visited);  }
    public StringBuilder _printMach( StringBuilder sb, BitSet visited ) { throw Utils.TODO(); }
    public int log_size() { return _con.log_size();  }

    @Override
    public boolean eq(Node n) {
        MemOpNode mem = (MemOpNode)n; // Invariant
        return _alias==mem._alias && super.eq(mem);
    }

    @Override
    int hash() { return _alias ^ super.hash(); }

    @Override
    public Parser.ParseException err() {
        Type ptr = ptr()._type;
        // Already an error, but better error messages come from elsewhere
        if( ptr == Type.BOTTOM ) return null;
        if( ptr.isHigh() ) return null; // Assume it will fall to not-null
        // Better be a not-nil TMP
        if( !(ptr instanceof TypeMemPtr tmp && tmp.notNull()) )
            return Parser.error( "Might be null accessing '" + _name + "'",_loc);
        // Sane field
        if( off() instanceof ConFldOffNode && CodeGen.CODE._phase.ordinal() > CodeGen.Phase.Opto.ordinal() )
            return Parser.error("Accessing unknown field '"+_name+"' from '*"+tmp._obj._name+"'",_loc);
        return null;

    }
}
