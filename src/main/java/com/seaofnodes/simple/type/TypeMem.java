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

    // Addresses reachable through this memory. EMPTY means no exposed address;
    // FULL means any address. These discovery sets join by union, independently
    // of the field contents. They use the same interned sets as function types.
    public int[] _escFs, _escAs;

    private static final Ary<TypeMem> FREE = new Ary<>(TypeMem.class);
    private TypeMem(int alias, Type t, int[] fs, int[] as) { super(TMEM); init(alias,t,fs,as); }
    private TypeMem init(int alias, Type t, int[] fs, int[] as) {
        assert alias>1 || (t==null || t==Type.TOP || t==Type.BOTTOM || t instanceof TypeStruct);
        _alias = alias;
        _t = t;
        _escFs=fs; _escAs=as;
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

    public static TypeMem malloc(int alias, Type t, int[] fs, int[] as) {
        return FREE.isEmpty() ? new TypeMem(alias,t,fs,as) : FREE.pop().init(alias,t,fs,as);
    }
    public static TypeMem make(int alias, Type t) {
        return make(alias,t,t.isHigh() ? XInt.EMPTY : XInt.FULL,t.isHigh() ? XInt.EMPTY : XInt.FULL);
    }
    public static TypeMem make(int alias, Type t, int[] fs, int[] as) {
        TypeMem f = malloc(alias,t,fs,as);
        TypeMem f2 = f.intern();
        if( f2==f ) return f;
        return VISIT.isEmpty() ? f2.free(f) : f2.delayFree(f);
    }

    // Private memory describes one allocation. It is passed explicitly to
    // initializers; it never joins the public heap until EscapeNode.
    public static TypeMem makePrivate(TypeStruct shape) {
        return make(1,shape,XInt.EMPTY,XInt.EMPTY).escapes(shape);
    }

    public TypeMem escapes(Type value) {
        int[] fs=_escFs, as=_escAs;
        if( value instanceof TypeMem m ) {
            fs=XInt.meet(fs,m._escFs); as=XInt.meet(as,m._escAs);
        } else if( value instanceof TypeFunPtr f ) {
            fs=XInt.meet(fs,f.fidxs());
        } else if( value instanceof TypeMemPtr p ) {
            return escapes(p._obj);
        } else if( value instanceof TypeStruct s ) {
            if( s._open && !s.isAry() ) return make(_alias,_t,XInt.FULL,XInt.FULL);
            for( Field f : s._fields ) {
                // A constructor has a hidden memory result, never a public
                // callable address. Constant methods need no memory Store.
                if( f._t instanceof TypeFunPtr fn && fn.ret() instanceof TypeMem ) continue;
                as=XInt.make(as,f._alias);
                if( f._one && f._t instanceof TypeFunPtr fn ) fs=XInt.meet(fs,fn.fidxs());
            }
        } else if( value==Type.BOTTOM || value==TypeScalar.BOT ) {
            fs=XInt.FULL; as=XInt.FULL;
        }
        return make(_alias,_t,fs,as);
    }

    public TypeMem withContents(int alias, Type contents) {
        return make(alias,contents,_escFs,_escAs);
    }

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
    public static final TypeMem START = make(1,Type.BOTTOM,XInt.EMPTY,XInt.EMPTY);

    public static void gather(ArrayList<Type> ts) {
        ts.add(make(2,Type.NIL)); ts.add(make(2,TypeInteger.ZERO)); ts.add(BOT);
        ts.add(START); ts.add(make(2,TypeInteger.ZERO,XInt.make(3),XInt.make(5)));
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
        int alias = _alias==that._alias ? _alias :
            (_alias==1 && _t==Type.TOP) ? that._alias :
            (that._alias==1 && that._t==Type.TOP) ? _alias : 1;
        Type mt = _alias==that._alias || alias!=1 ? _t.meet(that._t) : Type.BOTTOM;
        return make(alias,mt,XInt.meet(_escFs,that._escFs),XInt.meet(_escAs,that._escAs));
    }

    @Override
    Type xdual() { return malloc(_alias,_t.dual(),XInt.dual(_escFs),XInt.dual(_escAs)); }

    @Override TypeMem rdual() {
        if( _dual!=null ) return dual();
        assert !_terned;
        TypeMem d = malloc(_alias,null,XInt.dual(_escFs),XInt.dual(_escAs));
        (_dual = d)._dual = this; // Cross link duals
        d._t = _t._terned ? _t.dual() : _t.rdual();
        return d;
    }

    @Override public boolean isHigh() { return _t.isHigh(); }
    @Override public int log_size() { throw Utils.TODO(); }
    @Override boolean _isFinal() { return _t._isFinal(); }
    @Override boolean _isGLB(boolean mem) { return _t instanceof TypeStruct s ? s.isGLB2() : _t._isGLB(true); }
    @Override public Type _glb(boolean mem) { return make(_alias,_t instanceof TypeStruct s ? s.glb2() : _t._glb(true)); }

    @Override int hash() { return 9876543 + _alias + _t.hashCode() + XInt.hash(_escFs) + XInt.hash(_escAs); }

    @Override boolean eq(Type t) {
        TypeMem that = (TypeMem) t; // Invariant
        return _alias == that._alias && _t == that._t && _escFs==that._escFs && _escAs==that._escAs;
    }

    @Override boolean cycle_eq(Type t) {
        TypeMem that = (TypeMem) t; // Invariant
        return _alias == that._alias && _escFs==that._escFs && _escAs==that._escAs && _t.cycle_eq(that._t);
    }

    @Override int nkids() { return 1; }
    @Override Type at( int idx ) { return _t; }
    @Override void set( int idx, Type t ) { _t = t; }

    @Override public SB _print(SB sb, BitSet visit, boolean html) {
        if( this==BOT ) return sb.p("MemBot");
        if( this==TOP ) return sb.p("MemTop");
        sb.p("#");
        if( _alias==0 ) return sb.p(_t._type==TTOP ? "TOP" : "BOT");
        _t.print(sb.p(_alias).p(":"),visit,html);
        XInt.str(sb.p(" f="),_escFs);
        return XInt.str(sb.p(" a="),_escAs);
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
        return make(_alias,_t._upgradeType(types),_escFs,_escAs);
    }
}
