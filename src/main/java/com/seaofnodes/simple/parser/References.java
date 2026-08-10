package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.codegen.CompUnit;
import com.seaofnodes.simple.codegen.ParseAll;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.ScopeNode.Kind;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import java.util.BitSet;

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
                String clzName = Parser.addClzPrefix(cu._cname);
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
        // A constructor may read the unfinished object, but each field it
        // reads must already be initialized on every path. Read the current
        // local value; other fields may still be waiting for their first write.
        if( P.structs.inExplicitConstructor() && var._uninit && missingInit(rvalue,new BitSet()) )
            throw Parser.error("Field '"+id+"' might not be initialized",var._loc);

        // Check for an instance field load
        int kx = P._scope.kindx(var); // Declaration scope
        int fx = P._scope.enclosingFunction(); // Enclosing function scope
        Kind kk = P._scope._kinds.at(kx);
        Kind fk = P._scope._kinds.at(fx);
        // Access instance field from 'self'
        if( kx+1 == fx && kk instanceof Kind.Func inst && FunNode.isInstance(inst._name) && fk instanceof Kind.Func method && !P.structs.inExplicitConstructor() )
            return parseField(P._scope.in(method._lexSize),id,pre);

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
            return P.expressions.parsePostfixMethod(rvalue,P._scope.in(P._scope.update(P._scope.var(selfx),null)));
        }
        // Assign-update direct into Scope
        if( var._final && !P._scope.canInit(var) )
            throw P.error("Cannot reassign final '"+var._name+"'");
        checkSharedFunction(var);
        Node op = P.expressions.opAssign(ch,rvalue, var.type() );
        P._scope.update(var,op);
        if( Parser.postfix(ch) ) rvalue.unkeep();
        return pre==0 && Parser.postfix(ch) ? rvalue : op;
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
        String name = def._name;
        // Lift expression, based on type
        Type decl = def.type(); // Declared type
        Node lift = P.conversions.convertExpr(expr.keep(), decl).keep();
        // Update
        P._scope.update(name,lift);

        // Check for storing into an instance field instead of a local var
        int kx = P._scope.kindx(def); // Declaration scope
        int fx = P._scope.enclosingFunction(); // Enclosing function scope
        Kind kk = P._scope._kinds.at(kx);
        Kind fk = P._scope._kinds.at(fx);
        // Access instance field from 'self'
        if( kx+1 == fx && kk instanceof Kind.Func inst && FunNode.isInstance(inst._name) &&
            fk instanceof Kind.Func method && !P.structs.inExplicitConstructor() ) {
            // Named field offset on an unknown struct type
            TypeMemPtr self = (TypeMemPtr)P._scope.var(fk._lexSize).type();
            TypeStruct sobj = (TypeStruct)Parser.TYPES.get(self._obj._name);
            Field fld = sobj.field(name);
            int alias = fld._alias;
            Node ptr = P._scope.in(method._lexSize);
            Node off = P.fldoff(ptr,name);
            Node prior = P.mem().keep();
            Node st = new StoreNode(P.loc(), name, alias, decl, P.ctrl(), prior, ptr, off, lift, P.structs.inExplicitConstructor()).peephole().keep();
            P.storeMem(st,alias,prior);
            st.unkeep();
            prior.unkeep();
        }
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
        if( P.structs.inExplicitConstructor() && privateSelf(expr) ) return privateField(name,pre);
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
        if( fld==null && ts!=null && !Parser.startsClzPrefix(ts._name) ) {
            Type clz0 = Parser.TYPES.get(Parser.addClzPrefix(ts._name));
            if( clz0 instanceof TypeStruct clz && (fld = P.types.latestStruct(clz).field(name)) != null ) {
                expr.unkeep();
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
        if( (ts==null || ts==TypeStruct.BOT || ts.isHigh() || Parser.startsClzPrefix(ts._name)) &&
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
            Node mem = P.mem();
            // Store to field
            mem.keep();
            if( expr._type instanceof TypeMemPtr view && view._ro && P.ctrl()._type!=Type.XCONTROL )
                throw P.error("Cannot modify final field '"+name+"'");
            Node st = new StoreNode(P.loc(), name, alias, decl, P.ctrl(), mem, expr.unkeep(), off.unkeep(), lift, false).peephole().keep();
            P.storeMem(st,alias,mem);
            st.unkeep();
            mem.unkeep();

            return val.unkeep();        // "obj.a = expr" returns the expression while updating memory
        }

        // Memory for load
        Node mem = P.mem();
        // Loading from a constant array, the declared type is the
        // meet-over-elements and not the array itself.
        if( name=="[]" && decl instanceof TypeConAry conary )
            decl = conary.elem();

        // Load field
        Node load = P.peep(new LoadNode(P.loc(),name, alias, P.ctrl(), mem, expr, off));

        // Check for assign-update, "ptr.fld += expr" or "ary[idx]++"
        char ch = pre==0 ? P._lexer.matchOperAssign() : (char)pre;
        if( ch!=0 ) {
            if( fld!=null && decl==Type.BOTTOM )
                throw P.error( "'" + ts._name + "' is not fully initialized, field '" + fld._fname + "' needs to be set in a constructor" );
            Node op = P.expressions.opAssign(ch,load, decl ).keep();
            mem.keep();
            if( expr._type instanceof TypeMemPtr view && view._ro && P.ctrl()._type!=Type.XCONTROL )
                throw P.error("Cannot modify final field '"+name+"'");
            Node st = new StoreNode(P.loc(), name, alias, decl, P.ctrl(), mem, expr.unkeep(), off.unkeep(), op, false).peephole().keep();
            P.storeMem(st,alias,mem);
            st.unkeep();
            mem.unkeep();

            op.unkeep(); // A narrow Store may have folded away the expression's conversion.
            if( Parser.postfix(ch) ) load.unkeep();
            return pre==0 && Parser.postfix(ch) ? load : op;
        }
        off.unkill();
        // Might be a method call, so pass expr as 'self'
        return P.expressions.parsePostfixMethod(load,expr.unkeep());
    }

    Node defaultSelf() {
        int selfx = P._scope._kinds.at(P._scope.enclosingFuncOrDecl())._lexSize;
        return P._scope.in(P._scope.update(P._scope.var(selfx),null));
    }

    private boolean privateSelf(Node ptr) {
        while( ptr instanceof ConvertNode || ptr instanceof ReadOnlyNode || ptr instanceof GuardNode ) ptr = ptr.in(1);
        Node self = defaultSelf();
        while( self instanceof ConvertNode || self instanceof ReadOnlyNode || self instanceof GuardNode ) self = self.in(1);
        return ptr==self;
    }

    // An explicit self.x read sees the same current value as bare x. Loading
    // the allocation here would miss updates still held in local SSA values.
    private Node privateField(String name, int pre) {
        int idx = P.structs.fieldVarIdxBefore(P._scope._kinds.at(P._scope.enclosingFuncOrDecl())._lexSize,name);
        if( idx<0 ) throw P.error("Cannot resolve field '"+name+"'");
        Var v = P._scope.var(idx);
        if( pre==0 && P.matchOpx('=','=') ) {
            checkSharedFunction(v);
            Node value = P.expressions.parseAssignment().keep();
            P._scope.update(v,P.conversions.convertExpr(value,v.type()));
            return value.unkeep();
        }
        Node value = P._scope.in(P._scope.update(v,null));
        if( v._uninit && missingInit(value,new BitSet()) )
            throw Parser.error("Field '"+name+"' might not be initialized",v._loc);
        char ch = pre==0 ? P._lexer.matchOperAssign() : (char)pre;
        if( ch!=0 ) {
            checkSharedFunction(v);
            Node op = P.expressions.opAssign(ch,value,v.type());
            P._scope.update(v,op);
            if( pre!=0 && Parser.postfix(ch) ) value.unkeep();
            return pre==0 && Parser.postfix(ch) ? value.unkeep() : op;
        }
        return P.expressions.parsePostfixMethod(value,defaultSelf());
    }

    private void checkSharedFunction(Var v) {
        if( P._scope.canInit(v) && v._final && v.type() instanceof TypeFunPtr t && t.isConstant() )
            throw P.error("Cannot reassign shared function '"+v._name+"'");
    }

    // A loop Phi is temporarily bottom before its backedge is parsed. That is
    // not an unwritten field: follow its live inputs to the actual defaults.
    private boolean missingInit(Node value, BitSet seen) {
        if( value==null || seen.get(value._nid) ) return false;
        seen.set(value._nid);
        if( value instanceof PhiNode phi && !(value instanceof ParmNode) ) {
            for( int i = 1; i<phi.nIns(); i++ )
                if( phi.region().in(i)!=null && phi.region().in(i)._type!=Type.XCONTROL && missingInit(phi.in(i),seen) )
                    return true;
            return false;
        }
        return !Structs.initialized(value._type);
    }

    Ary<FRefNode> resolve() {
        // All still-unresolved forward refs
        // should immediately resolve to a file-based name which is a Simple
        // class type.
        Ary<FRefNode> frefs = new Ary<>(FRefNode.class);
        for( FRefNode fref : _frefs ) {
            if( fref.nIns() != 1 && fref._con == FRefNode.FREF_TYPE ) continue;
            CompUnit cu = ParseAll.findCompUnitOrThrow(P._code,P._ref,fref._name);
            fref._con = TypeMemPtr.make((byte)2,TypeStruct.make(Parser.addClzPrefix(cu._cname),true),true);
            frefs.push(fref);
        }

        return frefs;
    }
}
