package com.seaofnodes.simple.type;

import com.seaofnodes.simple.codegen.GlobalBits;
import java.util.IdentityHashMap;

public class TypeLocalizer {
    private final String _cname;
    private final GlobalBits _aliases, _fileAliases;
    private final GlobalBits _fidxs  , _fileFidxs;
    private final GlobalBits _rpcs   , _fileRpcs;
    private final IdentityHashMap<Type,Type> _map = new IdentityHashMap<>();

    public TypeLocalizer(String cname,
                         GlobalBits aliases, GlobalBits fileAliases,
                         GlobalBits fidxs  , GlobalBits fileFidxs,
                         GlobalBits rpcs   , GlobalBits fileRpcs) {
        _cname = cname;
        _aliases = aliases; _fileAliases = fileAliases;
        _fidxs   = fidxs  ; _fileFidxs   = fileFidxs  ;
        _rpcs    = rpcs   ; _fileRpcs    = fileRpcs   ;
    }

    public Type[] localize(Type[] roots) {
        Type.BOTTOM.recurOpen();
        Type[] locals = new Type[roots.length];
        for( int i=0; i<roots.length; i++ )
            locals[i] = copy(roots[i]);
        for( Type local : _map.values() )
            if( local._type >= Type.TCYCLIC )
                Type.VISIT.put(local._uid,local);
        Type.BOTTOM.recurClose(locals);
        return locals;
    }

    public Type local(Type t) { return _map.getOrDefault(t,t); }

    private Type copy(Type t) {
        Type x = _map.get(t);
        if( x != null ) return x;
        if( t.is_nokids() ) return t;

        Type y;
        if( t instanceof Field fld ) {
            y = Field.raw(fld._fname,null,localInt(fld._alias,_aliases,_fileAliases),fld._final);
            _map.put(t,y);
            y.set(0,copy(fld._t));
            return y;
        }
        if( t instanceof TypeStruct ts ) {
            Field[] flds = new Field[ts._fields.length];
            y = TypeStruct.raw(ts._name,ts._open,flds);
            _map.put(t,y);
            for( int i=0; i<flds.length; i++ )
                flds[i] = (Field)copy(ts._fields[i]);
            return y;
        }
        if( t instanceof TypeMemPtr tmp ) {
            y = TypeMemPtr.malloc(tmp._nil,null,tmp._one);
            _map.put(t,y);
            y.set(0,copy(tmp._obj));
            return y;
        }
        if( t instanceof TypeFunPtr tfp ) {
            Type[] sig = new Type[tfp._sig.length];
            y = TypeFunPtr.malloc(tfp._nil,tfp._open,sig,null,localXInt(tfp._fidxs,_fidxs,_fileFidxs));
            _map.put(t,y);
            for( int i=0; i<sig.length; i++ )
                sig[i] = copy(tfp._sig[i]);
            y.set(sig.length,copy(tfp._ret));
            return y;
        }
        if( t instanceof TypeMem mem ) {
            TypeMem base = mem._alias < 1 ? mem.makeFrom(XInt.EMPTY,XInt.EMPTY).escapesFrom(mem._t) : mem;
            int[] escFs = mem._alias < 1
                ? base._escFs
                : (base._escFs == XInt.EMPTY ? XInt.EMPTY : XInt.high(XInt.EMPTY));
            int[] escAs = mem._alias < 1
                ? base._escAs
                : (base._escAs == XInt.EMPTY ? XInt.EMPTY : XInt.high(XInt.EMPTY));
            y = TypeMem.malloc(localInt(mem._alias,_aliases,_fileAliases),null,mem._one,mem._final,
                               escFs,escAs);
            _map.put(t,y);
            y.set(0,copy(mem._t));
            return y;
        }
        if( t instanceof TypeRPC rpc ) {
            y = TypeRPC.raw(localXInt(rpc._rpcs,_rpcs,_fileRpcs));
            _map.put(t,y);
            return y;
        }
        if( t instanceof TypeTuple tt ) {
            Type[] ts = new Type[tt._types.length];
            y = TypeTuple.raw(ts);
            _map.put(t,y);
            for( int i=0; i<ts.length; i++ )
                ts[i] = copy(tt._types[i]);
            return y;
        }
        return t;
    }

    private int localInt(int x, GlobalBits globals, GlobalBits locals) {
        return x < GlobalBits.RESERVED ? x : (globals.hasLocal(x) ? locals.local(globals,x) : x);
    }

    private int[] localXInt(int[] xs, GlobalBits globals, GlobalBits locals) {
        if( xs == XInt.EMPTY ) return xs;
        if( xs == XInt.FULL  ) return xs;
        int[] ys = XInt.EMPTY;
        boolean external = xs == XInt.FULL || XInt.isHigh(xs);
        for( int bit = XInt.nextFinite(xs,-1); bit >= 0; bit = XInt.nextFinite(xs,bit) ) {
            if( bit < GlobalBits.RESERVED )
                ys = XInt.make(ys,bit);
            else if( globals.isLocal(bit,_cname) )
                ys = XInt.make(ys,locals.local(globals,bit));
            else
                external = true;
        }
        return external ? XInt.high(ys) : ys;
    }

}
