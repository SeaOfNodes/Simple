package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.BAOS;
import java.util.*;

// Pool objects have identity independently of their initial field values.
// References in their contents use the same symbols in ELF and emulator images.
public final class StaticData {
    public static final class ObjectData {
        public final Type type;
        public final String symbol;
        public final boolean readOnly, external;
        public final int alignment, size;
        public int offset;
        ObjectData(Type t, String name, boolean ro, boolean ext) {
            type=t; symbol=name; readOnly=ro; external=ext;
            alignment=t.alignment();
            int align=1<<alignment;
            size=t instanceof TypeStruct ts ? (Math.max(1,ts.size())+align-1)&-align : align;
        }
    }
    public record Relocation(ObjectData object, int offset, int logSize, Type value) {}

    final Encoding enc;
    final LinkedHashMap<Object,ObjectData> objects = new LinkedHashMap<>();
    final ArrayList<Relocation> relocations = new ArrayList<>();
    final String prefix;

    public ArrayList<ObjectData> entries() {
        ArrayList<ObjectData> entries=new ArrayList<>(objects.values());
        entries.sort(Comparator.comparingInt((ObjectData obj) -> obj.alignment).reversed());
        return entries;
    }

    StaticData(Encoding enc) {
        this.enc=enc;
        prefix=enc._code.entryClinitName()+"$DATA$";
    }

    static boolean isClass(Type t) {
        return t instanceof TypeStruct ts && Parser.startsClzPrefix(ts._name);
    }

    static CompUnit owner(CodeGen code,TypeStruct ts) {
        return owner(code,ts._name.substring(Parser.CLZ.length()));
    }
    static CompUnit owner(CodeGen code,String name) {
        if(name==null) return null;
        CompUnit owner=null;
        for( CompUnit cu : code._compunits.values() )
            if( cu._cname!=null && (name.equals(cu._cname) || name.startsWith(cu._cname+".")) &&
                (owner==null || cu._cname.length()>owner._cname.length()) ) owner=cu;
        return owner;
    }

    // A final field whose declaration already denotes this exact constant is
    // available as that value to every ideal Load. Materialize it in the object
    // image instead of emitting the redundant initializer Store. This lowering
    // leaves serialized ideal IR intact, so imports retain their memory edges.
    static boolean preinitialized(CodeGen code,StoreNode st) {
        if( !(st.ptr()._type instanceof TypeMemPtr ptr) || !ptr._one || !isClass(ptr._obj) ) return false;
        CompUnit owner=owner(code,ptr._obj);
        if( owner==null || owner._src==null ) return false;
        if( !(Parser.TYPES.get(ptr._obj._name) instanceof TypeStruct ts) ) return false;
        Field fld=ts.field(st._name);
        if( fld==null || !fld._final || !fld._t.isConstant() || !st.val()._type.isConstant() ) return false;
        Type value=st.val()._type, declared=fld._t;
        if( declared instanceof TypeFunPtr fp && value instanceof TypeFunPtr vp ) return fp.fidx()==vp.fidx();
        if( declared instanceof TypeMemPtr ptr0 && value instanceof TypeMemPtr ptr1 )
            return pointerConstant(ptr0) && ptr0._obj==ptr1._obj;
        return declared==value;
    }

    ObjectData object(Type t) {
        Object key=isClass(t) ? ((TypeStruct)t)._name
            : t instanceof TypeFunPtr fp ? functionSymbol(fp) : t;
        ObjectData old=objects.get(key);
        if( old!=null ) return old;
        boolean clz=isClass(t);
        if( clz && Parser.TYPES.get(((TypeStruct)t)._name) instanceof TypeStruct canonical ) t=canonical;
        CompUnit owner=clz ? owner(enc._code,(TypeStruct)t)
            : t instanceof TypeFunPtr fp ? owner(enc._code,enc._code._fidxs.owner(fp.fidx())) : null;
        String symbol=clz ? ((TypeStruct)t)._name.substring(Parser.CLZ.length())+".$class"
            : t instanceof TypeFunPtr fp ? functionSymbol(fp) : prefix+objects.size();
        ObjectData obj=new ObjectData(t,symbol,!clz && (!(t instanceof TypeStruct ts) || ts.isConstant()),
                                      owner!=null && owner._src==null);
        objects.put(key,obj);
        // Register before following fields: class objects may refer to each other.
        if( !obj.external && t instanceof TypeStruct ts )
            for( Field f : ts._fields )
                if( !f._extern && f._t.isConstant() ) target(f._t);
        return obj;
    }

    private static boolean pointerConstant(TypeMemPtr ptr) {
        return ptr.notNull() && (isClass(ptr._obj) || (ptr._obj.isConstant() && (!ptr._obj.isAry() || ptr._obj.isConAry())));
    }

    private void target(Type t) {
        if( t instanceof TypeMemPtr ptr && pointerConstant(ptr) ) object(ptr._obj);
        if( t instanceof TypeFunPtr fp && fp.notNull() &&
            enc._code.link(fp)==null && !enc._code._externFunc.containsKey(fp.fidx()) )
            // A dead function still needs a unique non-null identity for comparisons.
            object(fp);
    }

    void write() {
        ArrayList<Encoding.Relo> refs=new ArrayList<>(enc._bigCons.values());
        refs.sort(Comparator.comparingInt(r -> r._op._nid));
        for( Encoding.Relo ref : refs ) object(ref._t);
        // Definitions belong to their compilation unit, even if every local use folded.
        ArrayList<CompUnit> units=new ArrayList<>(enc._code._compunits.values());
        units.sort(Comparator.comparing(cu -> cu._cname==null ? "" : cu._cname));
        for( CompUnit cu : units ) if( cu._src!=null && cu._clz!=null ) object(cu._clz);
        for( ObjectData obj : entries() ) {
            if( obj.external ) continue;
            BAOS bits=obj.readOnly ? enc._cpool : enc._sdata;
            Encoding.padN(1<<obj.alignment,bits);
            obj.offset=bits.size();
            if( obj.type instanceof TypeStruct ts ) writeStruct(obj,ts,bits);
            else Encoding.addN(obj.alignment,obj.type,bits);
            while( bits.size()<obj.offset+obj.size ) bits.write(0);
        }
        for( Encoding.Relo ref : refs ) {
            ObjectData obj=object(ref._t);
            ref._target=obj.offset;
            ref._opStart=enc.opStart(ref._op);
        }
    }

    private void writeStruct(ObjectData obj, TypeStruct ts, BAOS bits) {
        int[] layout=ts.layout();
        for( int idx : layout ) {
            if( idx==ts._fields.length ) continue;
            Field f=ts._fields[idx];
            if( f._extern ) continue;
            int off=ts.offset(idx);
            while( bits.size()<obj.offset+off ) bits.write(0);
            if( f._fname.equals("[]") ) ((TypeConAry)f._t).write(bits);
            else {
                Type value=f._t.isConstant() ? f._t : f._t.makeZero();
                int log=f._t.log_size();
                Encoding.addN(log,value,bits);
                if( (value instanceof TypeFunPtr fp && fp.notNull()) ||
                    (value instanceof TypeMemPtr ptr && pointerConstant(ptr)) )
                    relocations.add(new Relocation(obj,off,log,value));
            }
        }
        while( bits.size()<obj.offset+ts.size() ) bits.write(0);
    }

    String functionSymbol(TypeFunPtr fp) {
        String ext=enc._code._externFunc.get(fp.fidx());
        return ext!=null ? ext : enc._code._fidxs.symbol(fp.fidx());
    }

    String targetSymbol(Type value) {
        if( value instanceof TypeMemPtr ptr ) return object(ptr._obj).symbol;
        TypeFunPtr fp=(TypeFunPtr)value;
        return enc._code.link(fp)==null && !enc._code._externFunc.containsKey(fp.fidx())
            ? object(fp).symbol : functionSymbol(fp);
    }

    void link(int cpool, int sdata) {
        for( Relocation rel : relocations ) {
            long address;
            if( rel.value instanceof TypeMemPtr ptr ) address=address(object(ptr._obj),cpool,sdata);
            else {
                TypeFunPtr fp=(TypeFunPtr)rel.value;
                String ext=enc._code._externFunc.get(fp.fidx());
                FunNode fun=enc._code.link(fp);
                if( ext!=null ) address=enc.externalAddress(ext);
                else if( fun==null ) address=address(object(fp),cpool,sdata);
                else {
                    if( !enc._code.owns(fun) ) throw new IllegalArgumentException("Unlinked function "+functionSymbol(fp));
                    address=enc.opStart(fun);
                }
            }
            // Emulator native-call sentinels are signed 32-bit addresses.
            if( rel.logSize==2 && (address<Integer.MIN_VALUE || address>0xffff_ffffL) )
                throw new IllegalArgumentException("Function address outside 32 bits: "+address);
            BAOS bits=rel.object.readOnly ? enc._cpool : enc._sdata;
            int off=rel.object.offset+rel.offset;
            for( int i=0; i<(1<<rel.logSize); i++ ) bits.buf()[off+i]=(byte)(address>>>(8*i));
        }
    }

    int address(ObjectData obj,int cpool,int sdata) {
        if( obj.external ) throw new IllegalArgumentException("Unlinked class object "+obj.symbol);
        return obj.offset+(obj.readOnly ? cpool : sdata);
    }
}
