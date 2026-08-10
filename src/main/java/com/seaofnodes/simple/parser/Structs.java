package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.ScopeNode.Kind;
import com.seaofnodes.simple.type.*;

/** Struct and constructor declarations, field discovery, and initialization checks. */
final class Structs {
    private final Parser P;

    // Constructor declaration: new TypeName = { args -> body };
    record Constructor(String name, Lexer body) {}
    Constructor _pendingCtor;

    Structs(Parser P) { this.P = P; }

    /**
     * Parse a struct declaration, and return the following statement.
     * Structs cannot be redefined, but can be nested.
     *
     * @return zero
     */
    Node parseStruct( ) {
        // keyword "struct" already parsed, so expect the struct name next.
        String typeName = P.requireId();
        P.require("{");

        // create type class:NESTED.typeName; no fields.

        String old = P._nestedType;
        String fullName = P._scope.depth()==1 && P._nestedType.endsWith(typeName) ? old : (old+"."+typeName).intern();
        P._nestedType = fullName; // Recursive structs start with this as their basename

        ReturnNode ret = parseInitializer( false, fullName );

        P._nestedType = old;    // Pop nested stack

        // Insert a field into class:NESTED.typeName of "val typeName = fcn-ptr-to-init"
        // Function does malloc internally.
        // Future self: means subtyping has to deal with "who does the malloc"
        String clzName = TypeStruct.addClzPrefix(fullName);
        TypeFunPtr sig = ret.fun().sig();
        TypeStruct ts = (TypeStruct)Parser.TYPES.get(clzName);
        String initName = initFieldName(fullName);
        Field oldFld = ts==null ? null : ts.field(initName);
        Field fld = oldFld==null
            ? Field.make(initName,sig,P._code.alias(P._ref._cname),true)
            : oldFld.makeFrom(sig);
        Field ctorFld = ts==null ? null : ts.field("<ctor>");
        // A same-named struct shares its file's class object and flag.
        Field flag = ts==null ? null : ts.field("$init");
        ts = ctorFld==null
            ? TypeStruct.make(clzName,false,fld)
            : TypeStruct.make(clzName,false,fld,ctorFld);
        if( flag!=null ) ts = ts.add(flag);
        TypeFunPtr alloc = P.allocations.makeAllocator((TypeStruct)Parser.TYPES.get(fullName),ret,ctorFld);
        // The visible constructor name denotes allocation, not the hidden initializer.
        sig = alloc;
        ts = ts.replace(fld.makeFrom(alloc));
        ts = ts.add(Field.make("<new>",alloc,P._code.alias(P._ref._cname),true));
        Parser.TYPES.put(clzName,ts);

        // Insert a field in the containing class with the nested class type.
        // This is basically sugar for "val CLZNAME = <new>"
        // and is a nicer version of updateSelfAsFieldsDiscovered.
        P._scope.define(typeName, sig, true, new FunPtrNode(sig,P._code._start,P._code.link(alloc).ret()).peephole(), P.loc());
        Var binding = P._scope.lookup(typeName);
        declareField(binding,P._scope.in(binding),false);

        P.require("}");
        return P.require(P._code.ZERO,";");
    }

    boolean inStructDeclaration() {
        return P._scope.klast() instanceof Kind.Func fun && FunNode.isInstance(fun._name);
    }

    Node parseConstructorDeclaration() {
        TypeStruct self = P._scope.constructorSelf();
        assert self != null;
        String name = P.requireId();
        String expect = initFieldName(self._name);
        if( name != expect )
            throw P.error("Constructor name '" + name + "' must match type name '" + expect + "'");
        P.require("=");
        if( _pendingCtor!=null ) throw P.error("Redefining constructor '"+name+"'");
        _pendingCtor = new Constructor(name,P.loc());
        // Parse the body after all field declarations are known, so self.x
        // and x resolve the same field even when x is declared below this body.
        P.require("{");
        P._lexer.skipTo('{','}',"constructor body");
        P.require("}");
        P.require(";");
        return P._code.ZERO;
    }

    void finishConstructor() {
        if( _pendingCtor==null ) return;
        Constructor ctor = _pendingCtor;
        _pendingCtor = null;
        Lexer after = P._lexer;
        P._lexer = new Lexer(ctor.body());
        defineConstructor(ctor.name());
        P._lexer = after;
    }

    private void defineConstructor(String name) {
        TypeStruct self = P._scope.constructorSelf();
        Node ctor = P.functions.parseConstructor(name,true);
        TypeFunPtr sig = (TypeFunPtr)ctor._type;
        String clzName = TypeStruct.addClzPrefix(self._name);
        TypeStruct clz = (TypeStruct)Parser.TYPES.get(clzName);
        Field old = clz==null ? null : clz.field("<ctor>");
        if( old != null )
            throw P.error("Redefining constructor '" + name + "'");
        Field fld = Field.make("<ctor>",sig,P._code.alias(P._ref._cname),true);
        Parser.TYPES.put(clzName,clz==null ? TypeStruct.make(clzName,true,fld) : clz.add(fld));
        P.require(ctor,";");
    }

    // Parse a struct declaration (not an allocation); file-level is a
    // class-init, and scope structs are normal
    ReturnNode parseInitializer( boolean isClz, String typeName ) {
        // Record & restore global state set during parsing the <init> code
        Node oldCtrl= P._scope.ctrl().keep();
        Node oldMem = P._scope.mem ().keep();

        // Make the future clazz/instance struct.
        TypeStruct tself = (TypeStruct)Parser.TYPES.get(typeName);
        assert tself==null || tself._open;
        // Close an existing open struct, or define a forward ref
        tself = tself==null ? TypeStruct.make(typeName,true) : tself.defined();

        // Class object keeps a runtime flag `$init` to signal the <clinit> has run.
        if( isClz )
            tself = tself.add(Field.make("$init",TypeInteger.BOOL,P._code.alias(P._ref._cname),false));
        Parser.TYPES.put(typeName, tself);

        // Signature of the default constructor
        TypeFunPtr sig = structInitSig(isClz,tself);
        // Function and argument names of default constructor
        String fname = (typeName + (isClz ? ".<clinit>" : ".<init>")).intern();
        String[] ids = new String[]{"self", isClz ? "arg" : "#selfMem"};

        // Struct decls look like function bodies.  Parse function body normally.
        ReturnNode ret = P.functions.parseBody(fname,sig,P.loc(),ids);
        // Unwind global state.
        P.ctrl(oldCtrl.unkeep());
        P.mem (oldMem .unkeep());
        return ret;
    }

    // <cl/init> signature: { self arg/selfMem -> BOT/selfMem }
    // Self-memory is the rare *private* (never aliased) memory for the new object.
    private TypeFunPtr structInitSig( boolean isClz, TypeStruct tself ) {
        // One object, unknown contents. The signature must not promise that
        // its declared field values have already been supplied.
        Type targ = isClz ? TypeInteger.BOT : TypeMem.makePrivate(Type.BOTTOM);
        // Return for private memory has to name all escaped fields, which are not known yet.
        // Escape ALL fields, and sharpen later.
        Type tret = isClz ? Type.BOTTOM     : TypeMem.makePrivate(Type.BOTTOM);
        Type[] targs = new Type[]{ TypeMemPtr.make((byte)2,tself,isClz).withPartial(!isClz), targ };
        // FIDX for FREFs is in the global namespace, needs the correct clz name.
        String clzBare = tself._name;
        int order = 3; // 3 is always the <init> order, 2nd in the file just behind the <clinit>
        if( isClz ) {
            assert TypeStruct.startsClzPrefix( clzBare );
            clzBare = clzBare.substring(TypeStruct.CLZ.length()).intern();
            order = 2; // 2 is always the <clinit> order, first in file and skipping reserved #1
        }
        return TypeFunPtr.make1((byte)2, true, targs, tret, P._code.fidx(clzBare,order));
    }

    int updateSelfAsFieldsDiscovered( String typeName, int nvar ) {
        int lex = P._scope.klast()._lexSize + 2/*Skip self and selfMem*/;
        // Load self type in and out of the TYPES hashtable.
        TypeStruct tself = (TypeStruct)Parser.TYPES.get(typeName);
        // Start looking for newly declared vars at the lexical scope start,
        // skipping two ("self" and "selfmem") and fields seen already.
        while( lex + nvar < P._scope.nIns() ) {
            Var v = P._scope.var(lex + nvar++);
            // Field with sharper type
            Field old = tself.field(v._name);
            Field fld = v._extern!=0
                ? Field.make(v._name,v.type(),v._extern,false,true)
                : old == null
                ? Field.make(v._name,v.type(),P._code.alias(P._ref._cname),v._final)
                : old.makeFrom(v.type());
            tself = tself.addOrUpdate(fld);
        }
        Parser.TYPES.put(typeName,tself);
        return nvar;
    }

    void finishInitializer( String typeName, boolean isClz, ParmNode self ) {
        int lex = P._scope.klast()._lexSize;
        assert P._scope.var(lex)._name=="self";
        TypeStruct tself = (TypeStruct)Parser.TYPES.get(typeName);
        int base = lex+2;       // Skip ctrl, mem
        // Default <init>/<clinit> defines the struct and can sharpen fields.
        // A named constructor only initializes the already-closed struct.
        for( int i = 0; i<tself.nkids(); i++ ) {
            Field tfld = tself._fields[i];
            int vidx = fieldVarIdx(base,tfld._fname);
            if( vidx == -1 )
                continue;
            Var v = P._scope.var(vidx);
            // Forward-ref fields where placed in tself but will get promoted
            // to an outer scope - and should not be in tself.
            if( v._fref )
                tself = tself.remove(i--);
            else if( v.type() != tfld._t ) {
                assert v.type().isa(tfld._t);
                tself = tself.replace( tfld.makeFrom( v.type() ) );
            }
        }

        // Close Struct type after parsing struct body.
        Parser.TYPES.put( tself._name, tself = tself.close() );
        // Improve the receiver type in scope.
        // Class self-type is a singleton pointer.  Instance <init> receives the
        // broad allocation pointer, while private memory tracks initialized fields.
        self._declaredType = self._type = isClz ? TypeMemPtr.make((byte)2,tself,true) : TypeMemPtr.make(constructorRecv(tself)).withPartial(true);
        FunNode fun = self.fun();
        fun.setSig(fun.sig().makeFrom(self._type,0));

        // When can _returnScope be null here? A never-exit constructor will
        // not have any returns, and thus no need to gather values and store
        // them into the (never) constructed object
        if( P.functions._returnScope==null )
            return;

        // Class singleton fields retain their store-at-exit path. Instance
        // initializers already returned their current private memory.
        if( !isClz ) return; // Instance fields already have their Stores.
        Node mmm = P.functions._returnScope.mem(1);

        // Store constructor results into fields
        for( int i = 0; i<tself.nkids(); i++ ) {
            Field fld = tself._fields[i];
            int vidx = fieldVarIdx(base,fld._fname);
            if( vidx == -1 )
                continue;
            Var v = P._scope.var(vidx);
            if( v._fref || v._extern!=0 ) continue; // C storage has no Simple initializer
            Node val = P.functions._returnScope.in(vidx);
            // A numeric merge can still be untyped while parsing. Retain its
            // declared conversion at the helper boundary, as assignments do.
            if( fld._t instanceof TypeInteger || fld._t instanceof TypeFloat )
                val = P.conversions.convertExpr(val,fld._t);
            // Store value into extended struct
            Node prior = mmm.keep();
            Node st = P.peep(new StoreNode(null, fld._fname, fld._alias, fld._t, null, prior, self, ConFldOffNode.make(tself,fld._fname), val, fld._final));
            mmm = P.mergeAlias(prior,fld._alias,st);
            prior.unkeep();
        }
        mmm = mmm.peephole();

        P.functions._returnScope.mem(mmm);
    }

    // Instance declarations establish field metadata and emit their default
    // Stores immediately. Required fields emit no Store until assigned.
    void declareField(Var var, Node value, boolean uninit) {
        if( !(P._scope.klast() instanceof Kind.Func kind) || !FunNode.isInstance(kind._name) ) return;
        TypeStruct self = P._scope.constructorSelf();
        TypeStruct shape = (TypeStruct)Parser.TYPES.get(self._name);
        Field old = shape.field(var._name);
        Field fld = old==null ? Field.make(var._name,var.type(),P._code.alias(P._ref._cname),var._final) : old.makeFrom(var.type());
        Parser.TYPES.put(shape._name,shape = shape.addOrUpdate(fld));
        if( uninit || var._extern!=0 ) return;
        Node ptr = P.references.defaultSelf();
        Node mem = P.construction.selfMem().keep();
        Node st = new StoreNode(var._loc,fld._fname,fld._alias,fld._t,P.ctrl(),mem,ptr,
                                ConFldOffNode.make(shape,fld._fname),value,true).peephole();
        P.construction.store(ptr,fld._alias,st,mem);
        mem.unkeep();
    }

    void beginClassInit(FunNode fun, Lexer loc) {
        // Each initializer establishes its parent before setting its own
        // run-once flag. Keep this in the body so every execution strategy
        // shares the same ordering, including direct entry into a child.
        if( P._ref._par!=null ) {
            String pname = TypeStruct.addClzPrefix(P._ref._par._cname);
            TypeStruct parent = (TypeStruct)Parser.TYPES.get(pname);
            if( parent==null ) parent = TypeStruct.make(pname,true);
            Node ptr = P.con(TypeMemPtr.make((byte)2,parent,true)).keep();
            P.classInit(loc,ptr);
            ptr.unkeep();
        }
        Node self = fun.parm(2);
        Field flag = ((TypeMemPtr)self._type)._obj.field("$init");
        Node prior = P.mem().keep();
        Node st = P.peep(new StoreNode(loc,"$init",flag._alias,flag._t,P.ctrl(),prior,self,
                                    P.fldoff(self,"$init"),P.con(1),false));
        P.storeMem(st,flag._alias,prior);
        prior.unkeep();
    }

    private int fieldVarIdx( int base, String name ) {
        for( int i = base; i<P._scope.nIns(); i++ )
            if( P._scope.var(i)._name == name )
                return i;
        return -1;
    }

    boolean isConstructorDecl(String name) {
        TypeStruct self = P._scope.constructorSelf();
        return self != null && name == initFieldName(self._name);
    }

    boolean inExplicitConstructor() {
        Kind.Func func = (Kind.Func)P._scope._kinds.at(P._scope.enclosingFuncOrDecl());
        return isExplicitConstructorName(func._name);
    }

    boolean isExplicitConstructorName(String funName) {
        TypeStruct self = P._scope.constructorSelf();
        return self != null && funName == (self._name+"."+initFieldName(self._name)).intern();
    }

    static TypeStruct constructorRecv(TypeStruct self) {
        Field[] fs = new Field[self._fields.length];
        for( int i = 0; i<fs.length; i++ ) {
            Field fld = self._fields[i];
            fs[i] = fld.makeFrom(fld._t.makeStorage());
        }
        return TypeStruct.make(self._name,self._open,fs);
    }

    private static String initFieldName( String typeName ) {
        if( TypeStruct.startsClzPrefix(typeName) )
            typeName = typeName.substring(TypeStruct.CLZ.length());
        int idx = typeName.lastIndexOf('.');
        return (idx == -1 ? typeName : typeName.substring(idx+1)).intern();
    }
}
