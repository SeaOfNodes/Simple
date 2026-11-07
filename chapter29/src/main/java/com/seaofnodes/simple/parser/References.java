package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.codegen.CompUnit;
import com.seaofnodes.simple.codegen.ParseAll;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.ScopeNode.Kind;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;

/** Name and field access, receiver selection, and lvalue checks. */
final class References {
    private final Parser P;
    // Forward references created during this unit.
    Ary<FRefNode> _frefs;

    References(Parser P) { this.P = P; }

    // Resolve a primary name, retaining its use site before speculative type lookup.
    Node parseName(String id, int pre, int pos, int pos2) {
        // Attempt a local var lookup first
        Var var = P._scope.lookup(id);
        if( var==null ) {
            // Not a local var, try again as a type name
            P.pos(pos);
            Type t = P.types.parseType();
            if( t!=null ) {
                // Static <clinit> typename lookup
                if( P.peek('.') ) // type.fld, which is really a field lookup in the class object
                    return P.expressions.parsePostfix(P.con(t));
                // Direct reference to a <clinit> type.  Convert the instance
                // type to a class type name
                if( !(t instanceof TypeMemPtr tmp) ) {
                    //return new FRefNode(addClzPrefix(tmp._obj._name), loc()).peephole();
                    // Not a local var, but yes a bare type... disallow a local
                    // FREF and return null, not-a-primary parse.
                    P.pos(pos);
                    return null;
                }
            }

            // Insert FREF outside enclosing Kind.Func scope.
            P.pos(pos2);
            t = FRefNode.FREF_TYPE;
            // Check if this is a class.
            CompUnit cu = ParseAll.findCompUnit(P._code,P._ref,id);
            if( cu!=null ) {
                // Must be a final class-type
                String clzName = TypeStruct.addClzPrefix(cu._cname);
                TypeStruct clz = (TypeStruct)Parser.TYPES.get(clzName);
                if( clz == null )
                    Parser.TYPES.put(clzName,clz = TypeStruct.make(clzName,true));
                t = TypeMemPtr.make((byte)2,clz,true);
            }
            // Define the FRef
            var = P._scope.defineFRef(id,t,true,P.loc());
            P.references._frefs.push((FRefNode)P._scope.in(var));
        }

        // Load local value
        Node rvalue = P._scope.in(var);
        if( var._extern!=0 )
            return parseField(rvalue,id,pre);
        if( var._fref )
            return P.expressions.parsePostfix(rvalue);
        Node fieldSelf = fieldSelf(var);
        if( fieldSelf!=null ) return parseField(fieldSelf,id,pre);
        int kx = P._scope.kindx(var);
        int fx = P._scope.enclosingFunction();

        // Check for a function-escaping variable; these require true
        // closures.  Final constants are OK; final vars require a hidden var
        // argument; not-final fields can just use an explicit struct arg.
        if( kx < fx && !P.structs.inExplicitConstructor() && // Out of scope
            !(var._final && rvalue._type.isConstant()) ) { // Not a final constant
            if( kx > 0  ) // Global scope is OK, only ever one of these (not one per function invoke)
                throw P.error("Variable '"+var._name+"' is out of function scope and must be a final constant");

            // var.idx==2 is self argument, 3 is args, 4 or more are now static globals
            if( var._idx >= 4 ) {
                // Read from global scope.
                // Surely a nicer way to get the top-level scope compile name?
                Var clzptr = P._scope.var( 2 );
                // Get the most recent sharpen global
                TypeStruct clz = (TypeStruct) Parser.TYPES.get( ((TypeMemPtr) clzptr.type())._obj._name );
                TypeMemPtr clztmp = TypeMemPtr.make( (byte) 2, clz, true );
                return parseField( P.con( clztmp ), id,pre );
                //return parseField(_scope.in(2),id);
            }
        }

        // Check for assign-update, x += e0;
        char ch = pre==0 ? P._lexer.matchOperAssign() : (char)pre;
        if( ch==0  ) {          // Normal primary, check for postfix updates
            int selfx = P._scope.kindFcn(var)._lexSize;
            // Might be a method call from inside a method, so no explicit "self".
            // Pass the in-scope "self"
            return P.expressions.parsePostfixMethod(P.construction.view(rvalue),P._scope.in(P._scope.update(P._scope.var(selfx),null)));
        }
        // Assign-update direct into Scope
        if( var._final && !P._scope.canInit(var) )
            throw P.error("Cannot reassign final '"+var._name+"'");
        checkSharedFunction(var);
        Node op = P.expressions.opAssign(ch,rvalue, var.type() );
        P._scope.update(var,op);
        if( Lexer.postfix(ch) ) rvalue.unkeep();
        return pre==0 && Lexer.postfix(ch) ? rvalue : op;
    }

    void checkAssignment(Var def) {
        String name = def._name;
        // A final field may receive its first value in its constructor.
        if( P._scope.in(def._idx)._type!=Type.TOP && def._final &&
            // Inside an allocation, final assign is OK, outside nope.
            // The parseNew() call added the allocation scope
            !P._scope.canInit(def) )
            throw P.error("Cannot reassign final '"+name+"'");
        checkSharedFunction(def);

    }

    Node assign(Var def, Node expr) {
        Node self = fieldSelf(def);
        if( self!=null ) {
            TypeStruct ts = P.types.latestStruct(((TypeMemPtr)self._type)._obj);
            Field fld = ts.field(def._name);
            Node val = expr.keep();
            Node lift = P.conversions.convertExpr(val,def.type());
            Node mem = P.construction.storeMemory(self).keep();
            Node st = new StoreNode(P.loc(),fld._fname,fld._alias,fld._t,P.ctrl(),mem,self,
                                    ConFldOffNode.make(ts,fld._fname),lift,P.construction.own(self)).peephole();
            P.construction.store(self,fld._alias,st,mem);
            mem.unkeep();
            return val.unkeep();
        }
        String name = def._name;
        // Lift expression, based on type
        Type decl = def.type(); // Declared type
        Node lift = P.conversions.convertExpr(expr.keep(), decl).keep();
        // Update
        P._scope.update(name,lift);

        lift.unkill();

        // Return un-lifted expr
        return expr.unkeep();
    }

    /**
     * Parse postfix expression; this can be a field expression, an array
     * lookup or a postfix operator like '#'
     *
     * <pre>
     *     expr ['++' | '--' ]                 // Postfix unary write operator
     *     expr [ [op]= expr ]                 // Field reference
     *     expr ('[' expr ']')* [ [op]= expr ] // Array reference
     * </pre>
     */

    // pre is +/-1 for a prefix update, otherwise parse a postfix operation.
    Node parseField(Node expr, String name, int pre) {
        return parseField(expr,name,pre,P.loc());
    }

    Node parseField(Node expr, String name, int pre, Lexer site) {
        // Keep expr across possible updates
        expr.keep();

        // Get the best known TypeStruct from a pointer type.
        TypeStruct ts = null, ts2;
        if( expr._type instanceof TypeMemPtr tmp && (ts2 = ((TypeStruct)Parser.TYPES.get(tmp._obj._name))) != null )
            ts = (TypeStruct)tmp._obj.join(ts2);
        // A Load's flow type is intentionally not promised during parsing,
        // but its declaring field type is still known.  Recover that static
        // type for chained accesses such as `_grow(1).buf[len++]`; otherwise
        // the array Store is parsed as alias #1 and can hide the independent
        // side effect of the postfix `len++`.
        if( ts==null && expr instanceof LoadNode load ) {
            TypeStruct ots = load.off() instanceof ConFldOffNode coff ? coff._ts : null;
            if( ots==null && load.ptr()._type instanceof TypeMemPtr owner ) {
                Type owner0 = Parser.TYPES.get(owner._obj._name);
                ots = owner0 instanceof TypeStruct obj ? obj : owner._obj;
            }
            if( ots != null ) {
                Field loaded = ots.field(load._name);
                if( loaded != null && loaded._t instanceof TypeMemPtr ltmp ) {
                    Type loaded0 = Parser.TYPES.get(ltmp._obj._name);
                    ts = loaded0 instanceof TypeStruct obj ? obj : ltmp._obj;
                }
            }
        }

        Field fld = ts==null ? null : ts.field(name);
        if( fld==null && ts!=null && !TypeStruct.startsClzPrefix(ts._name) ) {
            Type clz0 = Parser.TYPES.get(TypeStruct.addClzPrefix(ts._name));
            if( clz0 instanceof TypeStruct clz && (fld = P.types.latestStruct(clz).field(name)) != null ) {
                expr.unkill();
                ts = P.types.latestStruct(clz);
                expr = P.con(TypeMemPtr.make((byte)2,ts,true)).keep();
            }
        }
        if( fld==null && ts!=null )
            fld = P.types.fieldOrOpen(ts,name,false);
        if( fld==null && ts!=null && !P.types.latestStruct(ts)._open )
            throw P.error("Accessing unknown field '"+name+"' from '*"+ts._name+"'");

        // We have to record "active touches" of a new class, to track when we
        // need to force class initialization.  Basically, we insert an CLInit,
        // and if it does not fold away it'll turn into a runtime check.
        Kind.Func func = (Kind.Func)P._scope._kinds.at(P._scope.enclosingFuncOrDecl());
        if( (ts==null || ts==TypeStruct.BOT || ts.isHigh() || TypeStruct.startsClzPrefix(ts._name)) &&
            !(ts!=null && (ts._name+".<clinit>").equals(func._name)) )
            P.classInit(site,expr);

        // With no field, we will update the bulk memory
        int alias = fld==null ?  1          : fld._alias;
        Type decl = fld==null ? Type.BOTTOM : fld._t;

        // Field offset; fixed for structs, computed for arrays
        Node off = (name=="[]"
            // Array element math, with unknown base offset and shift amount.
            ? P.peep(new AddNode(P.fldoff(expr,"[]"),P.peep(new ShlNode(null,P.require(P.expressions.parseAssignment(),"]"),P.fldoff(expr,"<<")))))
            // Named field offset on an unknown struct type
            : P.fldoff(expr,name)).keep();

        // Disambiguate "obj.fld==x" boolean test from "obj.fld=x" field assignment
        if( pre==0 && P.matchOpx('=','=') ) {
            // Field assignment
            Node val = P.expressions.parseAssignment().keep();
            // Lift value for store
            Node lift = fld==null ? val : new ConvertNode(fld._t,val).peephole();
            // Memory for store, post assignment expression
            Node mem = P.construction.storeMemory(expr);
            // Store to field
            mem.keep();
            if( !P.construction.own(expr) && expr._type instanceof TypeMemPtr view && view._ro && P.ctrl()._type!=Type.XCONTROL )
                throw P.error("Cannot modify final field '"+name+"'");
            Node st = new StoreNode(P.loc(), name, alias, decl, P.ctrl(), mem, expr.unkeep(), off.unkeep(), lift, P.construction.own(expr)).peephole().keep();
            P.construction.store(expr,alias,st,mem);
            st.unkeep();
            mem.unkeep();

            return val.unkeep();        // "obj.a = expr" returns the expression while updating memory
        }

        // Memory for load
        Node mem = P.construction.memory(expr);
        // Loading from a constant array, the declared type is the
        // meet-over-elements and not the array itself.
        if( name=="[]" && decl instanceof TypeConAry conary )
            decl = conary.elem();

        // Load field
        Node load = P.peep(new LoadNode(P.loc(),name,alias,P.ctrl(),mem,expr,off));

        // Check for assign-update, "ptr.fld += expr" or "ary[idx]++"
        char ch = pre==0 ? P._lexer.matchOperAssign() : (char)pre;
        if( ch!=0 ) {
            if( fld!=null && decl==Type.BOTTOM )
                throw P.error( "'" + ts._name + "' is not fully initialized, field '" + fld._fname + "' needs to be set in a constructor" );
            Node op = P.expressions.opAssign(ch,load, decl ).keep();
            mem.keep();
            if( !P.construction.own(expr) && expr._type instanceof TypeMemPtr view && view._ro && P.ctrl()._type!=Type.XCONTROL )
                throw P.error("Cannot modify final field '"+name+"'");
            Node st = new StoreNode(P.loc(), name, alias, decl, P.ctrl(), mem, expr.unkeep(), off.unkeep(), op, P.construction.own(expr)).peephole().keep();
            P.construction.store(expr,alias,st,mem);
            st.unkeep();
            mem.unkeep();

            op.unkeep(); // A narrow Store may have folded away the expression's conversion.
            if( Lexer.postfix(ch) ) load.unkeep();
            return pre==0 && Lexer.postfix(ch) ? load : op;
        }
        off.unkill();
        // Might be a method call, so pass expr as 'self'
        return P.expressions.parsePostfixMethod(load,expr.unkeep());
    }

    Node defaultSelf() {
        int selfx = P._scope._kinds.at(P._scope.enclosingFuncOrDecl())._lexSize;
        return P._scope.in(P._scope.update(P._scope.var(selfx),null));
    }

    // Fields are lvalues in memory, including inside their initializer and
    // explicit constructor. Scope slots retain declaration metadata only.
    private Node fieldSelf(Var var) {
        int kx = P._scope.kindx(var), fx = P._scope.enclosingFuncOrDecl();
        ScopeNode.Kind kind = P._scope._kinds.at(kx);
        if( !(kind instanceof Kind.Func inst) || !FunNode.isInstance(inst._name) || var._idx<inst._lexSize+2 ) return null;
        if( kx==fx || kx+1==fx ) return defaultSelf();
        return null;
    }

    private void checkSharedFunction(Var v) {
        if( P._scope.canInit(v) && v._final && v.type() instanceof TypeFunPtr t && t.isConstant() )
            throw P.error("Cannot reassign shared function '"+v._name+"'");
    }

    Ary<FRefNode> resolve() {
        // All still-unresolved forward refs
        // should immediately resolve to a file-based name which is a Simple
        // class type.
        Ary<FRefNode> frefs = new Ary<>(FRefNode.class);
        for( FRefNode fref : _frefs ) {
            if( fref.nIns() != 1 && fref._con == FRefNode.FREF_TYPE ) continue;
            CompUnit cu = ParseAll.findCompUnitOrThrow(P._code,P._ref,fref._name);
            // Name lookup may already know this class's fields. Do not replace
            // that information with an empty placeholder before Iter runs.
            if( fref._con==FRefNode.FREF_TYPE )
                fref.liftType(TypeMemPtr.make((byte)2,TypeStruct.make(TypeStruct.addClzPrefix(cu._cname),true),true));
            frefs.push(fref);
        }

        return frefs;
    }
}
