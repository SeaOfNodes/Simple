package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.codegen.CompUnit;
import com.seaofnodes.simple.codegen.ParseAll;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;

/** Type syntax, qualified type lookup, and forward type declarations. */
final class Types {
    private final Parser P;

    Types(Parser P) { this.P = P; }

    // Parse a type or restore the cursor and return null. Unknown names need
    // a following binding to distinguish a forward type from an expression.

    // Structs have nested names, and can be discovered with a partial name
    // match.  Example, searching for a type name "C.D" in the namespace "A.B".
    // This will match "A.B.C.D", then "A.C.D", then "C.D".

    // Will also match external names like "sys.io" or "externModule.subtype.subtype"

    Type parseType() {
        if( P.peek('{') ) return parseFunctionType();

        int old1 = P.pos();
        boolean writable = P.match("!");
        boolean readonly = !writable && P.match("~");
        String tname = P._lexer.matchId();
        if( tname==null ) return P.posT(old1);

        Type t0 = Parser.TYPES.get(tname);
        if( Parser.KEYWORDS.contains(tname) ) {
            if( t0==null ) return P.posT(old1);
        } else {
            if( t0==Type.BOTTOM || t0==Type.TOP ) return t0; // var/val
            tname = typeName(tname).intern();
            t0 = Parser.TYPES.get(tname);
        }
        Type t1 = t0==null ? TypeStruct.forward(tname) : t0;

        // Structs are always actually references
        if( t1 instanceof TypeStruct ts1 )
            t1 = TypeMemPtr.make(ts1);

        // A qualifier before the name applies to the struct reference, not
        // to an enclosing array. Arrays have their own [] / [~] qualifier.
        if( t1 instanceof TypeMemPtr tmp ) {
            if( readonly || !writable ) t1 = tmp.makeRO();
        } else if( writable || readonly )
            return P.posT(old1);

        // Nest arrays and '?' as needed
        Type t2 = t1;
        while( true ) {
            if( P.match("?") ) {
                if( t2 instanceof TypeMemPtr tmp ) {
                    if( tmp.nullable() ) throw P.error("Type "+t2+" already allows null");
                    t2 = tmp.makeNullable();
                } else
                    throw P.error("Type "+t0+" cannot be null");
            } else if( P.match("[~]") ) {
                t2 = TypeMemPtr.make(typeAry(t2,true)).makeRO();
            } else if( P.match("[]") ) {
                t2 = TypeMemPtr.make(typeAry(t2,false));
            } else
                break;
        }

        // Check no forward ref
        if( t0 != null ) return t2;
        // Check valid forward ref, after parsing all the type extra bits.
        // Cannot check earlier, because cannot find required 'id' until after "[]?" syntax
        int old2 = P.pos();
        P.match("^"); // Constructor parameter qualifier; parsed by Functions.
        if( !P.match("!") ) P.match("~");
        String id = P._lexer.matchId();
        if( !(P.peek(',') || P.peek(';') || P.peek('=') || P.match("->")) || id==null )
            return P.posT(old1);  // Reset lexer to reparse
        P.pos(old2);              // Reset lexer to reparse
        // Yes a forward ref, so declare it
        forwardRefType(tname,(TypeStruct)((TypeMemPtr)t1)._obj);
        // Return the (array, final) type
        return t2;
    }

    TypeMemPtr forwardRefType( String tname, TypeStruct tself ) {
        Parser.TYPES.put(tname,tself);
        String clzName = TypeStruct.addClzPrefix( tname );
        // Only a struct declaration publishes an instance initializer.  A
        // forward type may name a file with just <clinit>, so do not invent
        // a constructor field (or overwrite an already parsed class).
        Parser.TYPES.putIfAbsent(clzName,TypeStruct.make(clzName,true));
        return TypeMemPtr.make(tself);
    }

    TypeMemPtr forwardRefType( String tname ) {
        tname = fullTypeName(tname);
        return forwardRefType(tname,TypeStruct.forward(tname));
    }

    String fullTypeName( String tname ) {
        String nest = P._nestedType, fullq;
        while( true ) {
            fullq = nest+"."+tname;
            if( Parser.TYPES.get(fullq) != null )
                return fullq.intern();
            int idx = nest.lastIndexOf('.');
            if( idx== -1 ) return fullq.intern();
            nest = nest.substring(0,idx);
        }
    }

    // Make an array type of t.  Always record a mutable version,
    // but return the requested version.
    TypeStruct typeAry( Type t, boolean efinal ) {
        // Non-null reference arrays are valid types, even though an ordinary
        // zero-filled `new T[len]` cannot construct one.  Allocation retains
        // that check in parseNew(); trusted runtimes (notably argv construction)
        // may fill the private array completely before publishing it.
        String tname = ("[]"+t.str()).intern();
        TypeStruct ta = (TypeStruct)Parser.TYPES.get(tname);
        if( ta==null ) {
            // Primitive arrays use the null compunit
            String rcname = Parser.TYPES.get(t) instanceof TypeStruct ? P._ref._cname : ("_."+t).intern();
            ta = TypeStruct.makeAry(tname,TypeInteger.U32,P._code.alias(rcname),t,P._code.alias(rcname), false );
            Parser.TYPES.put(tname,ta);
        }
        // Already have the aliases, just efinal is wrong
        return TypeStruct.makeAry(tname,TypeInteger.U32,ta._fields[0]._alias,t,ta._fields[1]._alias,efinal );
    }

    Type implicitSelfType() {
        TypeStruct self = P._scope.constructorSelf();
        // Ordinary instance methods accept the declared object shape.  The
        // constructor-only receiver starts at the low/default field values,
        // and is built explicitly by parseConstructor().
        return self == null ? TypePtr.PTR : TypeMemPtr.make(self);
    }

    // A function type is `{ type... -> type }` or `{ type }`.
    private Type parseFunctionType() {
        int old = P.pos();        // Record lexer position
        P.match("{");             // Skip already-peeked '{'
        Type t0 = parseType();       // Either return or first arg
        if( t0==null ) return P.posT(old); // Not a function
        Ary<Type> ts = new Ary<>(Type.class);
        // Always reserve a receiver slot. Outside constructor scopes the
        // broad signature also marks the hidden slot for the machine ABI.
        ts.push(implicitSelfType());
        if( P.match("}") )                 // No-arg function { -> type }
            return TypeFunPtr.make(P.match("?"),ts.asAry(),t0);
        ts.push(t0);            // First argument
        while( true ) {
            if( P.match("->") ) { // End of arguments, parse return
                Type ret = parseType();
                if( ret==null || !P.match("}") )
                    return P.posT(old); // Not a function
                return TypeFunPtr.make(P.match("?"),ts.asAry(),ret);
            }
            Type t1 = parseType();
            if( t1==null ) return P.posT(old); // Not a function
            ts.push(t1);
        }
    }

    // True if a TypeFunPtr, without advancing parser
    boolean isTypeFun() {
        int old = P.pos();
        if( parseFunctionType()==null ) return false;
        P.pos(old);
        return true;
    }

    TypeStruct latestStruct( TypeStruct ts ) {
        Type t = Parser.TYPES.get(ts._name);
        return t instanceof TypeStruct ts2 ? ts2 : ts;
    }

    Field fieldOrOpen( TypeStruct ts, String name, boolean xfinal ) {
        ts = latestStruct(ts);
        Field fld = ts.field(name);
        if( fld != null ) return fld;
        if( !ts._open || !ts._fref ) return null;
        fld = Field.make(name,Type.BOTTOM,P._code.alias(P._ref._cname),xfinal);
        Parser.TYPES.put(ts._name,ts = ts.add(fld));
        return fld;
    }

    // typeName: walk known local types first, then enclosing namespaces, then
    // file modules. A missing suffix of a known type belongs to field parsing.
    private String typeName(String name) {
        if( Parser.TYPES.containsKey(name) ) return dottedName(name,false);
        String root = P._nestedType;
        int dot = root.indexOf('.');
        if( dot!=-1 ) root = root.substring(0,dot);
        if( name.equals(root) ) return rootedName(name);

        for( String nest = P._nestedType; ; ) {
            String full = nest+"."+name;
            if( Parser.TYPES.containsKey(full) ) return dottedName(full,false);
            int idx = nest.lastIndexOf('.');
            if( idx==-1 ) break;
            nest = nest.substring(0,idx);
        }
        CompUnit cu = ParseAll.findCompUnit(P._code,P._ref,name);
        return dottedName(cu==null ? name : cu._cname,true);
    }

    private String dottedName(String name, boolean forward) {
        while( true ) {
            int old = P.pos();
            if( !P.match(".") ) return name;
            String part = P.matchId();
            if( part==null || (!forward && !Parser.TYPES.containsKey(name+"."+part)) ) {
                P.pos(old);
                return name;
            }
            name += "."+part;
        }
    }

    // A root-qualified name may introduce an as-yet-undeclared nested type
    // inside the current lexical namespace. Once forward, keep its full tail.
    private String rootedName(String name) {
        boolean forward = false;
        while( true ) {
            int old = P.pos();
            if( !P.match(".") ) return name;
            String part = P.matchId();
            if( part==null ) { P.pos(old); return name; }
            String full = name+"."+part;
            boolean known = Parser.TYPES.containsKey(full);
            if( !known && !forward &&
                !(P._nestedType.equals(name) || P._nestedType.startsWith(name+".")) ) {
                P.pos(old);
                return name;
            }
            name = full;
            forward |= !known;
        }
    }

}
