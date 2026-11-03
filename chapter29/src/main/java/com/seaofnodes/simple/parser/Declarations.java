package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.ScopeNode.Kind;
import com.seaofnodes.simple.type.*;

/** Local/field bindings and native symbol declarations. */
final class Declarations {
    private final Parser P;

    Declarations(Parser P) { this.P = P; }

    /**
     * Parse declaration or expression statement
     * declStmt = type var['=' exprAsgn][, var['=' exprAsgn]]* ';' | exprAsgn ';'
     * <p>
     * exprAsgn = var '=' exprAsgn | expr
     */
    Node parseDeclarationStatement() {
        int old = P.pos();
        Type t = P.types.parseType();
        if( P.peek('.') )         // Ambiguity static vars: "type.var", parse as expression
            { P.pos(old); t = null; }
        if( t == null )
            return P.require(P.expressions.parseAssignment(),";");

        // now parse var['=' asgnexpr] in a loop
        Node n = parseDeclaration(t);
        while( P.match(",") )
            n = parseDeclaration(t);
        return P.require(n,";");
    }

    // The binding modifier sits immediately before the name. It never
    // changes permissions on the referenced object.
    boolean bindingFinal(Type t) {
        if( P.match("!") ) return false;
        if( P.match("~") ) return true;
        return t==Type.TOP || t instanceof TypeNil;
    }

    /** Parse final: [!|~]var['=' asgn]
     */
    private Node parseDeclaration(Type t) {
        assert t!=null;
        // Has var/val instead of a user-declared type
        boolean inferType = t==Type.TOP || t==Type.BOTTOM;
        boolean xfinal = bindingFinal(t);
        Lexer loc = P.loc();
        String name = P.requireId();
        // Optional initializing expression follows
        boolean fld_final = false; // Field is final, but not deeply final
        boolean uninit = false;    // Required non-null constructor field
        Node expr;
        if( P.match("=") ) {
            if( isExternDecl() ) {
                if( !(t instanceof TypeFunPtr) ) {
                    Node ptr = externData(name,t);
                    // The lexical binding is a fixed address; its contents are mutable.
                    if( !P._scope.define(name,t,true,ptr,loc) )
                        throw Parser.error("Redefining name '"+name+"'",loc);
                    P._scope.lookup(name)._extern = P._code.externAlias(name);
                    return P._code.ZERO;
                }
                expr = externDecl(name,t);
                t = expr._type; // Upgrade declared type to the exact extern decl type
            } else if( P.structs.isConstructorDecl(name) ) {
                expr = P.functions.parseConstructor(name,false);
            } else {
                expr = P.expressions.parseAssignment();
            }
            // var/val, then type comes from expression
            if( inferType ) {
                if( expr._type==Type.NIL )
                    throw P.error("a not-null/non-zero expression");
                t = expr._type;
                if( !xfinal ) t = Conversions.mutableInferredType(t);
                // Instance fields may change until the constructor finishes.
                // Keep the declared family/access, not the default's constant
                // value or string contents. Class constants remain static.
                else if( P._scope.klast() instanceof Kind.Func fun && FunNode.isInstance(fun._name) && !(t instanceof TypeFunPtr) )
                    t = P.conversions.constructorFieldType(t);
            }

            // expr is a constant function
            if( t instanceof TypeFunPtr decl && expr._type instanceof TypeFunPtr tfp && tfp.isConstant() ) {
                t = decl.makeFrom(tfp.fidx());
                FunNode fun = P._code.link(tfp);
                if( fun != null )
                    fun.setName(name); // Assign debug name to Simple function
            }

        } else {
            // Since no initializer, not-final but might need an initializer

            // Need an expression to infer the type.
            // Also, if not-null then need an initializing expression.
            // Allowed in a class def, because type/init will happen in the constructor.
            if( (inferType || (t instanceof TypeNil tn && !tn.nullable() )) && !P._scope.inConstructor() )
                throw P.errorSyntax("=expression");
            // Initial value for uninitialized struct fields.
            expr = switch( t ) {
                // Required pointers retain their declared shape, but include
                // null until every constructor path assigns them.
            case TypeNil tn -> tn.nullable() ? P.con(Type.NIL) : P.con(t.meet(Type.NIL));
            case TypeInteger ti -> xfinal ? P.con(Type.BOTTOM) : P._code.ZERO;
            case TypeFloat tf -> P.con(xfinal ? Type.BOTTOM : TypeFloat.FZERO);
            // Bottom signals type inference: they must be initialized in
            // the constructor and that's when we'll discover the type.
            case Type tt -> { assert tt==Type.BOTTOM; yield P.con(tt); }
            };
            // Every non-null pointer without a default is constructor-required.
            // Without `!` it is also shallow-final; mutability and definite
            // initialization are independent properties.
            if( (xfinal && !(t instanceof TypeNil)) || (t instanceof TypeNil tn && !tn.nullable()) ) {
                uninit = true;
                if( xfinal ) fld_final = true;
            }
        }

        // Lift expression, based on type
        // Do not let an authoritative Convert hide the nullable
        // not-yet-initialized state from constructor-local type flow.
        Node lift = uninit ? expr : P.conversions.convertExpr(expr, t);

        // Define a new name
        if( !P._scope.define(name,t,xfinal || fld_final,lift, loc) )
            throw P.error("Redefining name '" + name + "'", loc);
        P.structs.declareField(P._scope.lookup(name),lift,uninit);
        return lift;
    }

    // Just after parsing "type foo = " can parse `"C"`
    private boolean isExternDecl( ) {
        int old = P.pos();
        String s = P._lexer.parseString();
        if( "C".equals(s) ) return true;
        P.pos(old);
        return false;
    }
    // External linked constant
    private ConstantNode externDecl( String ex, Type t ) {
        if( t instanceof TypeFunPtr tfp ) {  // Generic TFP from type parse
            int fidx = P._code.fidx(P._ref._cname); // Get a FIDX
            tfp = tfp.makeFrom(fidx);           // Become a constant
            P._code.externFunc(fidx,ex);          // Map fidx to extern name
            t = tfp;
        }
        return (ExternNode)(new ExternNode(t,ex).peephole());
    }

    // A lexical C binding has a singleton namespace with one external field.
    // The namespace occupies no storage; its field offset supplies the address.
    private Node externData(String name, Type t) {
        if( !(t instanceof TypeInteger) && !(t instanceof TypeFloat) )
            throw P.error("C data binding requires an integer or float type");
        String cname = ("C:"+name).intern();
        TypeStruct ts = TypeStruct.make(cname,false,Field.make(name,t,P._code.externAlias(name),false,true));
        Type old = Parser.TYPES.putIfAbsent(cname,ts);
        if( old!=null && old!=ts )
            throw P.error("Conflicting types for C data '"+name+"'");
        return P.con(TypeMemPtr.make((byte)2,ts,true));
    }
}
