package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.Utils;
import com.seaofnodes.simple.util.BAOS;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Forward-Ref
 */
public class TypeFRef extends Type {
    public String _name;

    private static final Ary<TypeFRef> FREE = new Ary<>(TypeFRef.class);
    private TypeFRef(String name) { super(TFREF); init(name); }
    private TypeFRef init(String name) { assert name!=null; _name = name; return this; }
    public static TypeFRef malloc(String name) { return FREE.isEmpty() ? new TypeFRef(name) : FREE.pop().init(name); }
    public static TypeFRef make(String name) {
        TypeFRef i = malloc(name);
        TypeFRef t2 = i.intern();
        return t2==i ? i : t2.free(i);
    }
    @Override TypeFRef free(Type t) {
        TypeFRef i = (TypeFRef)t;
        i._name = null;
        i._hash = 0;
        i._dual = null;
        FREE.push(i);
        return this;
    }

    public final static TypeFRef BOT = make("$");
    public final static TypeFRef TEST = make(" test");

    public static void gather(ArrayList<Type> ts) { ts.add(TEST); ts.add(BOT); }

    @Override public String str() { return "$"+_name; }

    @Override public boolean isHigh() { return _name.charAt(0)=='~'; }
    @Override boolean _isGLB(boolean mem) { return true; }
    @Override public boolean isFRef() { return true; }

    @Override public int log_size() { throw Utils.TODO(); }

    @Override
    public Type xmeet(Type other) {
        // Invariant from caller: 'this' != 'other' and same class (TypeFRef)
        TypeFRef fref = (TypeFRef)other; // Contract
        assert _name!=fref._name && !_name.equals(fref._name);
        if( this==BOT.dual() ) return fref;
        if( fref==BOT.dual() ) return this;
        return BOT;
    }

    @Override TypeFRef xdual() {
        return malloc(_name.charAt(0)=='~' ? _name.substring(1) : "~"+_name );
    }

    @Override int TAGOFF() { return 1; }
    @Override public void packed( BAOS baos, HashMap<String,Integer> strs, HashMap<Integer,Integer> aliases ) {
        baos.write(TAGOFFS[_type]).packed2(strs.get(_name));
    }

    static Type packed( int tag, BAOS bais, String[] strs ) { return make(strs[bais.packed2()]); }

    @Override int hash() { return _name.hashCode(); }
    @Override public boolean eq( Type t ) {
        return t instanceof TypeFRef fref && _name==fref._name;
    }
}
