package com.seaofnodes.simple.type;

import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.SB;
import com.seaofnodes.simple.util.Utils;
import java.util.ArrayList;
import java.util.BitSet;


/**
 * Public memory holds one field alias; private memory holds one whole object.
 */
public class TypeMem extends Type {

    // Which slice of memory?
    //  1 and TOP means no slice.
    //  1 and BOT means all memory.
    //  1 and TypeStruct means current fields of the private allocation.
    //  N means alias slice#N.
    public int _alias;
    public Type _t;       // Scalar public contents, or private field values.

    private static final Ary<TypeMem> FREE = new Ary<>(TypeMem.class);
    private TypeMem(int alias, Type t) { super(TMEM); init(alias,t); }
    private TypeMem init(int alias, Type t) {
        assert alias>1 || (t==null || t==Type.TOP || t==Type.BOTTOM || t instanceof TypeStruct);
        _alias = alias;
        _t = t;
        return this;
    }
    @Override TypeMem free(Type t) {
        TypeMem mem = (TypeMem)t;
        mem._alias= -99;
        mem._dual = null;
        mem._hash = 0;
        FREE.push(mem);
        return this;
    }
    @Override boolean isFree() { return _alias == -99; }

    public static TypeMem malloc(int alias, Type t) {
        return FREE.isEmpty() ? new TypeMem(alias,t) : FREE.pop().init(alias,t);
    }
    public static TypeMem make(int alias, Type t) {
        TypeMem f = malloc(alias,t);
        TypeMem f2 = f.intern();
        if( f2==f ) return f;
        return VISIT.isEmpty() ? f2.free(f) : f2.delayFree(f);
    }

    // Private memory describes one allocation. It is passed explicitly to
    // initializers; it never joins the public heap until EscapeNode.
    public static TypeMem makePrivate(TypeStruct shape) { return make(1,shape); }

    public static TypeStruct empty(TypeStruct shape) {
        Field[] fields = new Field[shape._fields.length];
        for( int i=0; i<fields.length; i++ )
            fields[i] = shape._fields[i].makeFrom(Type.BOTTOM);
        return TypeStruct.make(shape._name,shape._open,fields);
    }

    public static TypeStruct zeroed(TypeStruct shape) {
        Field[] fields=new Field[shape._fields.length];
        for( int i=0; i<fields.length; i++ ) {
            Field f=shape._fields[i];
            Type zero=f._t.makeZero();
            fields[i]=f.makeFrom(!f._final && f._t!=Type.BOTTOM && zero.isa(f._t) ? zero : Type.BOTTOM);
        }
        return TypeStruct.make(shape._name,shape._open,fields);
    }

    public static final TypeMem TOP = make(1, Type.TOP   );
    public static final TypeMem BOT = make(1, Type.BOTTOM);

    public static void gather(ArrayList<Type> ts) {
        ts.add(make(2,Type.NIL)); ts.add(make(2,TypeInteger.ZERO)); ts.add(BOT);
        ts.add(makePrivate(TypeStruct.TEST)); ts.add(makePrivate(empty(TypeStruct.TEST)));
    }

    @Override
    TypeMem xmeet(Type t) {
        TypeMem that = (TypeMem) t; // Invariant: TypeMem and unequal
        if( this==TOP ) return that;
        if( that==TOP ) return this;
        if( this==BOT ) return BOT;
        if( that==BOT ) return BOT;
        // Different slices lose their alias and contents together. Their
        // scalar meet alone cannot describe the unsplit bulk memory.
        if( _alias!=that._alias ) return BOT;
        int alias = _alias;
        Type mt = _t.meet(that._t);
        return make(alias,mt);
    }

    @Override
    Type xdual() { return _t._dual==_t ? this : malloc(_alias,_t.dual()); }

    @Override TypeMem rdual() {
        if( _dual!=null ) return dual();
        assert !_terned;
        TypeMem d = malloc(_alias,null);
        (_dual = d)._dual = this; // Cross link duals
        d._t = _t._terned ? _t.dual() : _t.rdual();
        return d;
    }

    @Override public boolean isHigh() { return _t.isHigh(); }
    @Override public int log_size() { throw Utils.TODO(); }
    @Override boolean _isFinal() { return _t._isFinal(); }
    @Override boolean _isGLB(boolean mem) { return _t instanceof TypeStruct s ? s.isGLB2() : _t._isGLB(true); }
    @Override public Type _glb(boolean mem) { return make(_alias,_t instanceof TypeStruct s ? s.glb2() : _t._glb(true)); }

    @Override int hash() { return 9876543 + _alias + _t.hashCode(); }

    @Override boolean eq(Type t) {
        TypeMem that = (TypeMem) t; // Invariant
        return _alias == that._alias && _t == that._t;
    }

    @Override boolean cycle_eq(Type t) {
        TypeMem that = (TypeMem) t; // Invariant
        return _alias == that._alias && _t.cycle_eq(that._t);
    }

    @Override int nkids() { return 1; }
    @Override Type at( int idx ) { return _t; }
    @Override void set( int idx, Type t ) { _t = t; }

    @Override public SB _print(SB sb, BitSet visit, boolean html) {
        if( this==BOT ) return sb.p("MemBot");
        if( this==TOP ) return sb.p("MemTop");
        sb.p("#");
        if( _alias==0 ) return sb.p(_t._type==TTOP ? "TOP" : "BOT");
        return _t.print(sb.p(_alias).p(":"),visit,html);
    }

    @Override public String str() { return toString(); }

    @Override Type _upgradeType(java.util.HashMap<String,Type> types) {
        if( _t instanceof TypeStruct old && types.get(old._name) instanceof TypeMemPtr ptr ) {
            // This struct records current contents, not a forward declaration.
            // Complete its field list without replacing writes with declared
            // types (or turning an unwritten field into an initialized one).
            TypeStruct shape=old.isAry() ? old : ptr._obj;
            Field[] fields=new Field[shape._fields.length];
            for( int i=0; i<fields.length; i++ ) {
                Field field=shape._fields[i], value=old.field(field._fname);
                fields[i]=field.makeFrom(value==null ? Type.BOTTOM : value._t._upgradeType(types));
            }
            return makePrivate(TypeStruct.make(shape._name,shape._open,fields));
        }
        return make(_alias,_t._upgradeType(types));
    }
}
