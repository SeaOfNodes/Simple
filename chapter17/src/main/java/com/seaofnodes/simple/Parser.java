package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

import java.util.*;

/**
 * The Parser converts a Simple source program to the Sea of Nodes intermediate
 * representation directly in one pass.  There is no intermediate Abstract
 * Syntax Tree structure.
 * <p>
 * This is a simple recursive descent parser. All lexical analysis is done here as well.
 */
public class Parser {

    /**
     * A Global Static, unique to each compilation.  This is a public, so we
     * can make constants everywhere without having to thread the StartNode
     * through the entire parser and optimizer.
     * <p>
     * To make the compiler multithreaded, this field will have to move into a TLS.
     */
    public static StartNode START;

    public static ConstantNode ZERO; // Very common node, cached here
    public static XCtrlNode XCTRL;   // Very common node, cached here

    // Next available memory alias number
    static int ALIAS;

    public StopNode STOP;

    // Debugger Printing.
    public static boolean SCHEDULED; // True if debug printer can use schedule info

    // The Lexer.  Thin wrapper over a byte[] buffer with a cursor.
    private final Lexer _lexer;

    /**
     * Current ScopeNode - ScopeNodes change as we parse code, but at any point of time
     * there is one current ScopeNode. The reason the current ScopeNode can change is to do with how
     * we handle branching. See {@link #parseIf()}.
     * <p>
     * Each ScopeNode contains a stack of lexical scopes, each scope is a symbol table that binds
     * variable names to Nodes.  The top of this stack represents current scope.
     * <p>
     * We keep a list of all ScopeNodes so that we can show them in graphs.
     * @see #parseIf()
     * @see #_xScopes
     */
    public ScopeNode _scope;

    /**
     * List of keywords disallowed as identifiers
     */
    private static final HashSet<String> KEYWORDS = new HashSet<>(){{
            add("bool");
            add("break");
            add("byte");
            add("continue");
            add("else");
            add("f32");
            add("f64");
            add("false");
            add("flt");
            add("i16");
            add("i32");
            add("i64");
            add("i8");
            add("if");
            add("int");
            add("new");
            add("null");
            add("return");
            add("struct");
            add("true");
            add("u1");
            add("u16");
            add("u32");
            add("u8");
            add("while");
        }};


    /**
     * We clone ScopeNodes when control flows branch; it is useful to have
     * a list of all active ScopeNodes for purposes of visualization of the SoN graph
     */
    public final Stack<ScopeNode> _xScopes = new Stack<>();

    ScopeNode _continueScope;
    ScopeNode _breakScope;      // Merge all the while-breaks here

    // Mapping from a type name to a Type.  The string name matches
    // `type.str()` call.  No TypeMemPtrs are in here, because Simple does not
    // have C-style '*ptr' references.
    public static HashMap<String, Type> TYPES;

    // Mapping from a type name to the constructor for a Type.
    public final HashMap<String, StructNode> INITS;


    public Parser(String source, TypeInteger arg) {
        Node.reset();
        IterPeeps.reset();
        SCHEDULED = false;
        _lexer = new Lexer(source);
        _scope = new ScopeNode();
        _continueScope = _breakScope = null;
        START = new StartNode(arg);
        STOP = new StopNode(source);
        ZERO = con(0).keep();
        XCTRL= new XCtrlNode().peephole().keep();
        ALIAS = 2; // alias 0 for the control, 1 for memory
        TYPES = defaultTypes();
        INITS = new HashMap<>();
    }

    public Parser(String source) {
        this(source, TypeInteger.BOT);
    }

    @Override
    public String toString() { return _lexer.toString(); }

    public static HashMap<String, Type> defaultTypes() {
        return new HashMap<>() {{
            put("bool",TypeInteger.U1 );
            put("byte",TypeInteger.U8 );
            put("f32" ,TypeFloat  .B32);
            put("f64" ,TypeFloat  .BOT);
            put("flt" ,TypeFloat  .BOT);
            put("i16" ,TypeInteger.I16);
            put("i32" ,TypeInteger.I32);
            put("i64" ,TypeInteger.BOT);
            put("i8"  ,TypeInteger.I8 );
            put("int" ,TypeInteger.BOT);
            put("u1"  ,TypeInteger.U1 );
            put("u16" ,TypeInteger.U16);
            put("u32" ,TypeInteger.U32);
            put("u8"  ,TypeInteger.U8 );
            put("val" ,Type.TOP);    // Marker type, indicates type inference
            put("var" ,Type.BOTTOM); // Marker type, indicates type inference
        }};
    }

    // Debugging utility to find a Node by index
    public static Node find(int nid) { return START.find(nid); }

    private Node ctrl() { return _scope.ctrl(); }
    private <N extends Node> N ctrl(N n) { return _scope.ctrl(n); }

    public StopNode parse() { return parse(false); }
    public StopNode parse(boolean show) {
        _xScopes.push(_scope);
        // Enter a new scope for the initial control and arguments
        _scope.push();
        ScopeMinNode mem = new ScopeMinNode();
        mem.addDef(null);
        _scope.define(ScopeNode.CTRL, Type.CONTROL   , false, new CProjNode(START, 0, ScopeNode.CTRL).peephole());
        mem.addDef(                                           new  ProjNode(START, 1, ScopeNode.MEM0).peephole());
        _scope.define(ScopeNode.MEM0, TypeMem.TOP    , false, mem                                    .peephole());
        _scope.define(ScopeNode.ARG0, TypeInteger.BOT, false, new  ProjNode(START, 2, ScopeNode.ARG0).peephole());

        // Parse whole program
        parseBlock(false);

        if( ctrl()._type==Type.CONTROL )
            STOP.addReturn(new ReturnNode(ctrl(), ZERO, _scope).peephole());
        _scope.kill();
        _xScopes.pop();
        for( StructNode init : INITS.values() )
            init.unkeep().kill();
        INITS.clear();
        if (!_lexer.isEOF()) throw error("Syntax error, unexpected " + _lexer.getAnyNextToken());
        STOP.peephole();
        if( show ) showGraph();
        return STOP;
    }

    /**
     * Parses a block
     *
     * <pre>
     *     '{' statements '}'
     * </pre>
     * Does not parse the opening or closing '{}'
     * @return a {@link Node} or {@code null}
     */
    private Node parseBlock(boolean inCon) {
        // Enter a new scope
        _scope.push(inCon);
        while (!peek('}') && !_lexer.isEOF())
            parseStatement();
        // Exit scope
        _scope.pop();
        return null;
    }

    /**
     * Parses a statement
     *
     * <pre>
     *     returnStatement | declStatement | blockStatement | ifStatement | expressionStatement
     * </pre>
     * @return a {@link Node} or {@code null}
     */
    private Node parseStatement() {
        if( false ) return null;
        else if (matchx("return")  ) return parseReturn();
        else if (match ("{")       ) return require(parseBlock(false),"}");
        else if (matchx("if")      ) return parseIf();
        else if (matchx("while")   ) return parseWhile();
        else if (matchx("for")     ) return parseFor();
        else if (matchx("break")   ) return parseBreak();
        else if (matchx("continue")) return parseContinue();
        else if (matchx("struct")  ) return parseStruct();
        else if (matchx("#showGraph")) return require(showGraph(),";");
        else if (matchx(";")       ) return null; // Empty statement
        // declarations of vars with struct type are handled in parseExpressionStatement due
        // to ambiguity
        else return parseExpressionStatement();
    }

    /**
     * Parses a while statement
     *
     * <pre>
     *     while ( expression ) statement
     * </pre>
     * @return a {@link Node}, never {@code null}
     */
    private Node parseWhile() {
        require("(");
        return parseLooping(false);
    }


    /**
     * Parses a for statement
     *
     * <pre>
     *     for( var x=init; test; incr ) body
     * </pre>
     * @return a {@link Node}, never {@code null}
     */
    private Node parseFor() {
        // {   var x=init,y=init,...;
        //     while( pred ) {
        //         body;
        //         next;
        //     }
        // }
        require("(");
        _scope.push();          // Scope for the index variables
        if( !match(";") )       // Can be empty init "for(;test;next) body"
            parseExpressionStatement(); // Non-empty init
        Node rez = parseLooping(true);
        _scope.pop();           // Exit index variable scope
        return rez;
    }

    // Shared by `for` and `while`
    private Node parseLooping( boolean doFor ) {

        var savedContinueScope = _continueScope;
        var savedBreakScope    = _breakScope;

        // Loop region has two control inputs, the first is the entry
        // point, and second is back edge that is set after loop is parsed
        // (see end_loop() call below).  Note that the absence of back edge is
        // used as an indicator to switch off peepholes of the region and
        // associated phis; see {@code inProgress()}.

        ctrl(new LoopNode(ctrl()).peephole()); // Note we set back edge to null here

        // At loop head, we clone the current Scope (this includes all
        // names in every nesting level within the Scope).
        // We create phis eagerly for all the names we find, see dup().

        // Save the current scope as the loop head
        ScopeNode head = _scope.keep();
        // Clone the head Scope to create a new Scope for the body.
        // Create phis eagerly as part of cloning
        _xScopes.push(_scope = _scope.dup(true)); // The true argument triggers creating phis

        // Parse predicate
        var pred = peek(';') ? con(1) : parseExpression();
        require( doFor ? ";" : ")" );

        // IfNode takes current control and predicate
        Node ifNode = new IfNode(ctrl(), pred.keep()).peephole();
        // Setup projection nodes
        Node ifT = new CProjNode(ifNode.  keep(), 0, "True" ).peephole().keep();
        Node ifF = new CProjNode(ifNode.unkeep(), 1, "False").peephole();

        // for( ;;next ) body
        int nextPos = -1, nextEnd = -1;
        if( doFor ) {
            // Skip the next expression and parse it later
            nextPos = pos();
            skipAsgn();
            nextEnd = pos();
            require(")");
        }

        // Clone the body Scope to create the break/exit Scope which accounts for any
        // side effects in the predicate.  The break/exit Scope will be the final
        // scope after the loop, and its control input is the False branch of
        // the loop predicate.  Note that body Scope is still our current scope.
        ctrl(ifF);
        _xScopes.push(_breakScope = _scope.dup());
        _breakScope.addGuards(ifF,pred,true); // Up-cast predicate

        // No continues yet
        _continueScope = null;

        // Parse the true side, which corresponds to loop body
        // Our current scope is the body Scope
        ctrl(ifT.unkeep());     // set ctrl token to ifTrue projection
        _scope.addGuards(ifT,pred.unkeep(),false); // Up-cast predicate
        parseStatement();       // Parse loop body
        _scope.removeGuards(ifT);

        // Merge the loop bottom into other continue statements
        if (_continueScope != null) {
            _continueScope = jumpTo(_continueScope);
            _scope.kill();
            _scope = _continueScope;
        }

        // Now append the next code onto the body code
        if( doFor ) {
            int old = pos(nextPos);
            if( !peek(')') )
              parseAsgn(null,false);
            assert pos() == nextEnd;
            pos(old);
        }

        // The true branch loops back, so whatever is current _scope.ctrl gets
        // added to head loop as input.  endLoop() updates the head scope, and
        // goes through all the phis that were created earlier.  For each phi,
        // it sets the second input to the corresponding input from the back
        // edge.  If the phi is redundant, it is replaced by its sole input.
        var exit = _breakScope;
        head.endLoop(_scope, exit);
        head.unkeep().kill();

        _xScopes.pop();       // Cleanup
        _xScopes.pop();       // Cleanup

        _continueScope = savedContinueScope;
        _breakScope = savedBreakScope;

        // At exit the false control is the current control, and
        // the scope is the exit scope after the exit test.
        _xScopes.pop();
        _xScopes.push(exit);
        _scope = exit;
        return _scope;
    }

    private ScopeNode jumpTo(ScopeNode toScope) {
        ScopeNode cur = _scope.dup();
        ctrl(XCTRL); // Kill current scope
        // Prune nested lexical scopes that have depth > than the loop head
        // We use _breakScope as a proxy for the loop head scope to obtain the depth
        while( cur._lexSize.size() > _breakScope._lexSize.size() )
            cur.pop();
        // If this is a continue then first time the target is null
        // So we just use the pruned current scope as the base for the
        // "continue"
        if( toScope == null )
            return cur;
        // toScope is either the break scope, or a scope that was created here
        assert toScope._lexSize.size() <= _breakScope._lexSize.size();
        toScope.ctrl(toScope.mergeScopes(cur).peephole());
        return toScope;
    }

    private void checkLoopActive() { if (_breakScope == null) throw Parser.error("No active loop for a break or continue"); }

    private Node parseContinue() { checkLoopActive(); return (_continueScope = require(jumpTo( _continueScope ),";"));  }
    private Node parseBreak   () {
        checkLoopActive();
        // At the time of the break, and loop-exit conditions are only valid if
        // they are ALSO valid at the break.  It is the intersection of
        // conditions here, not the union.
        _breakScope.removeGuards(_breakScope.ctrl());
        _breakScope = require(jumpTo(_breakScope ),";");
        _breakScope.addGuards(_breakScope.ctrl(), null, false);
        return _breakScope;
    }

    // Look for an unbalanced `)`, skipping balanced
    private Type skipAsgn() {
        int paren=0;
        while( true )
            // Next X char handles skipping complex comments
            switch( _lexer.nextXChar() ) {
            case Character.MAX_VALUE:
                throw Utils.TODO();
            case ')':
                if( --paren<0 )
                    return posT(pos()-1); // Leave the `)` behind
                break;
            case '(': paren ++; break;
            default: break;
            }
    }


    /**
     * Parses a statement
     *
     * <pre>
     *     if ( expression ) statement [else statement]
     * </pre>
     * @return a {@link Node}, never {@code null}
     */
    private Node parseIf() {
        // Parse predicate
        require("(");
        var pred = require(parseExpression(), ")");
        return parseTrinary(pred,true,"else");
    }

    // Parse a conditional expression, merging results.
    private Node parseTrinary( Node pred, boolean stmt, String fside ) {
        pred.keep();

        // IfNode takes current control and predicate
        Node ifNode = new IfNode(ctrl(), pred).peephole();
        // Setup projection nodes
        Node ifT = new CProjNode(ifNode.  keep(), 0, "True" ).peephole().keep();
        Node ifF = new CProjNode(ifNode.unkeep(), 1, "False").peephole().keep();
        // In if true branch, the ifT proj node becomes the ctrl
        // But first clone the scope and set it as current
        int ndefs = _scope.nIns();
        ScopeNode fScope = _scope.dup(); // Duplicate current scope
        _xScopes.push(fScope); // For graph visualization we need all scopes

        // Parse the true side
        ctrl(ifT.unkeep());     // set ctrl token to ifTrue projection
        _scope.addGuards(ifT,pred,false); // Up-cast predicate
        Node lhs = stmt ? parseStatement() : parseExpression().keep(); // Parse true-side
        _scope.removeGuards(ifT);

        ScopeNode tScope = _scope;

        // Parse the false side
        _scope = fScope;        // Restore scope, then parse else block if any
        ctrl(ifF.unkeep());     // Ctrl token is now set to ifFalse projection
        // Up-cast predicate, even if not else clause, because predicate can
        // remain true if the true clause exits: `if( !ptr ) return 0; return ptr.fld;`
        _scope.addGuards(ifF,pred,true);
        boolean doRHS = match(fside);
        Node rhs = doRHS
            ? (stmt ? parseStatement() : parseExpression())
            : (stmt ? null             : con(lhs._type.makeZero()));
        _scope.removeGuards(ifF);
        if( doRHS )
            fScope = _scope;
        pred.unkeep();

        // Check for `if(pred) int x=17;`
        if( tScope.nIns() != ndefs || fScope.nIns() != ndefs )
            throw error("Cannot define a new name on one arm of an if");

        // Merge results
        _scope = tScope;
        _xScopes.pop();       // Discard pushed from graph display

        RegionNode r = ctrl(tScope.mergeScopes(fScope));
        Node ret = stmt ? r : peep(new PhiNode("",lhs._type.meet(rhs._type),r,lhs.unkeep(),rhs));
        r.peephole();
        return ret;
    }

    /**
     * Parses a return statement; "return" already parsed.
     * The $ctrl edge is killed.
     *
     * <pre>
     *     'return' expr ;
     * </pre>
     * @return an expression {@link Node}, never {@code null}
     */
    private Node parseReturn() {
        var expr = require(parseExpression(), ";");
        Node ret = STOP.addReturn(new ReturnNode(ctrl(), expr, _scope).peephole());
        ctrl(XCTRL);            // Kill control
        return ret;
    }

    /**
     * Dumps out the node graph
     * @return {@code null}
     */
    Node showGraph() {
        System.out.println(new GraphVisualizer().generateDotOutput(STOP,_scope,_xScopes));
        return null;
    }

    /** Parse: name [=expr]
     */
    // t==null          ==>> re-assign: !final, expr=parse(), t:=def ._type
    // t==TOP           ==>> value    :  final, expr=parse(), t:=expr._type
    // t==BOT           ==>> variable : !final, expr=parse(), t:=expr._type
    // t==type && !expr ==>> declaratn: !final, expr=makeInit; t:=type;
    // t==type &&  expr ==>> declaratn:  final=inCon && noBang, expr=parse() ; t:=type;
    private Node parseAsgn(Type t, boolean hasBang) {
        boolean isDecl = t!=null;
        int old = pos();
        String name = requireId();

        boolean hasType = t!=null && t!=Type.TOP && t!=Type.BOTTOM; // User written type
        boolean hasExpr = !(peek(';') || peek(','));   // Expression follows
        if( hasExpr && !match("=") )                   // Something else
            { pos(old); return parseExpression(); }
        if( !hasExpr && !hasType )  throw errorSyntax("expression");
        Node expr = hasExpr ? parseExpression() : con(t.makeInit());

        // Final if no Bang AND TMP; primitives are not-final by default
        boolean xfinal = !hasBang && (t instanceof TypeMemPtr) && hasExpr;

        // Defining a new variable vs updating an old one
        if( t==null ) {
            // Lookup old variable
            ScopeMinNode.Var def = _scope.lookup(name);
            if( def==null )
                throw error("Undefined name '" + name + "'");
            // TOP fields are for late-initialized fields; these have never
            // been written to, and this must be the final write.  Other writes
            // outside the constructor need to check the final bit.
            if( _scope.in(def._idx)._type!=Type.TOP && def._final && !_scope.inCon() )
                throw error("Cannot reassign final '"+name+"'");
            t = def.type(); // Declared field type

        } else if( !hasType && hasExpr ) { // Either 'val' or 'var'
            // Either 'var' or 'val'; type comes from expression
            xfinal = t==Type.TOP; // xfinal === 'val'; otherwise 'var'
            t = expr._type.glb();
        }

        // Final is deep on ptrs
        if( xfinal && t instanceof TypeMemPtr tmp ) {
            t = tmp.makeRO();
            expr = peep(new ReadOnlyNode(expr));
        }

        // Auto-widen int to float
        if( expr._type instanceof TypeInteger && t instanceof TypeFloat )
            expr = peep(new ToFloatNode(expr));
        // Auto-narrow wide ints to narrow ints
        expr = zsMask(expr,t);
        // Auto-deepen forward ref types
        Type e = expr._type;
        if( e instanceof TypeMemPtr tmp && tmp.isFRef() )
            e = TYPES.get(tmp._obj._name);
        // Type is sane
        if( !e.isa(t) )
            throw error("Type " + e.str() + " is not of declared type " + t.str());

        // Define a new name, vs update an old one
        if( isDecl ) {
            if( !_scope.define(name,t,xfinal,expr) )
                throw error("Redefining name '" + name + "'");
        } else
            _scope.update(name,expr);
        return expr;
    }

    /** Parse final: [!]asgn
     */
    private Node parseFinal(Type t) {
        return parseAsgn(t,match("!"));
    }

    /**
     * Parses an expression statement or a declaration statement where type is a struct
     *
     * <pre>
     * type decl [, decl]*;  // Define many names with the same type
     * asgn;                 // Assign or load a variable
     * expr;                 // Something else
     * name;                 // Bare name is an error

     * </pre>
     * @return an expression {@link Node}, never {@code null}
     */
    private Node parseExpressionStatement() {
        Node n;
        Type t = type();
        if( t != null ) {
            // now parse final [, final]*
            n = parseFinal(t);
            while( match(",") )
                n = parseFinal(t);

            // Parse "asgn;" which is just "name = expr;"
        } else
            n = parseAsgn(null,false);
        return require(n,";");
    }


    /**
     * Parse a struct declaration, and return the following statement.
     * Only allowed in top level scope.
     * Structs cannot be redefined.
     *
     * @return The statement following the struct
     */
    private Node parseStruct() {
        if (_xScopes.size() > 1) throw errorSyntax("struct declarations can only appear in top level scope");
        String typeName = requireId();
        Type t = TYPES.get(typeName);
        if( t!=null && !(t instanceof TypeMemPtr tmp && tmp.isFRef() ) )
            throw errorSyntax("struct '" + typeName + "' cannot be redefined");

        // A Block scope parse, and inspect the scope afterward for fields.
        _scope.push(true);
        require("{");
        while (!peek('}') && !_lexer.isEOF())
            parseStatement();

        // Grab the declarations and build fields and a Struct
        int lexlen = _scope._lexSize.last();
        int varlen = _scope._vars._len;
        StructNode s = new StructNode();
        Field[] fs = new Field[varlen-lexlen];
        for( int i=lexlen; i<varlen; i++ ) {
            s.addDef(_scope.in(i));
            ScopeMinNode.Var v = _scope._vars.at(i);
            fs[i-lexlen] = Field.make(v._name,v.type(),ALIAS++,v._final);
        }
        TypeStruct ts = s._ts = TypeStruct.make(typeName, fs);
        TYPES.put(typeName, TypeMemPtr.make(ts));
        INITS.put(typeName,s.peephole().keep());
        // Done with struct/block scope
        require("}");
        require(";");
        _scope.pop();
        return null;
    }


    // Parse and return a type or null.  Valid types always are followed by an
    // 'id' which the caller must parse.  This lets us distinguish forward ref
    // types (which ARE valid here) from local vars in an (optional) forward
    // ref type position.

    // t = int|i8|i16|i32|i64|u8|u16|u32|u64|byte|bool | flt|f32|f64 | val | var | struct[?]
    private Type type() {
        int old1 = pos();
        String tname = _lexer.matchId();
        if( tname==null ) return null;
        // Convert the type name to a type.
        Type t0 = TYPES.get(tname);
        // No new types as keywords
        if( t0 == null && KEYWORDS.contains(tname) )
            return posT(old1);
        Type t1 = t0 == null ? TypeMemPtr.make(TypeStruct.makeFRef(tname)) : t0; // Null: assume a forward ref type
        // Nest arrays and '?' as needed
        while( true ) {
            assert !(t1 instanceof TypeStruct);
            if( match("?") ) {
                if( !(t1 instanceof TypeMemPtr tmp) )
                    throw error("Type "+t0+" cannot be null");
                if( tmp._nil ) throw error("Type "+t1+" already allows null");
                t1 = TypeMemPtr.make(tmp._obj,true);
                continue;
            }
            if( match("[]") ) {
                t1 = typeAry(t1);
                continue;
            }
            break;
        }

        // Check no forward ref
        if( t0 != null ) return t1;
        // Check valid forward ref, after parsing all the type extra bits.
        // Cannot check earlier, because cannot find required 'id' until after "[]?" syntax
        int old2 = pos();
        String id = _lexer.matchId();
        pos(old2);              // Reset lexer to reparse
        if( id==null )
            return posT(old1);  // Reset lexer to reparse
        // Yes a forward ref, so declare it
        TYPES.put(tname,t1);
        return t1;
    }

    // Make an array type of t
    private TypeMemPtr typeAry( Type t ) {
        if( t instanceof TypeMemPtr tmp && !tmp._nil )
            throw error("Arrays of reference types must always be nullable");
        String tname = "["+t.str()+"]";
        Type ta = TYPES.get(tname);
        if( ta != null ) return (TypeMemPtr)ta;
        // Need make an array type.
        TypeStruct ts = TypeStruct.makeAry(TypeInteger.BOT,ALIAS++,t,ALIAS++);
        assert ts.str().equals(tname);
        TypeMemPtr tary = TypeMemPtr.make(ts);
        TYPES.put(tname,tary);
        return tary;
    }


    /**
     * Parse an expression of the form:
     *
     * <pre>
     *     expr : bitwise [? expr [: expr]]
     * </pre>
     * @return an expression {@link Node}, never {@code null}
     */
    private Node parseExpression() {
        Node expr = parseBitwise();
        return match("?") ? parseTrinary(expr,false,":") : expr;
    }

    /**
     * Parse an bitwise expression
     *
     * <pre>
     *     bitwise : compareExpr (('&' | '|' | '^') compareExpr)*
     * </pre>
     * @return a bitwise expression {@link Node}, never {@code null}
     */
    private Node parseBitwise() {
        Node lhs = parseComparison();
        while( true ) {
            if( false ) ;
            else if( match("&") ) lhs = new AndNode(lhs,null);
            else if( match("|") ) lhs = new  OrNode(lhs,null);
            else if( match("^") ) lhs = new XorNode(lhs,null);
            else break;
            lhs.setDef(2,parseComparison());
            lhs = peep(lhs);
        }
        return lhs;
    }

    /**
     * Parse an expression of the form:
     *
     * <pre>
     *     expr : additiveExpr op additiveExpr
     * </pre>
     * @return an comparator expression {@link Node}, never {@code null}
     */
    private Node parseComparison() {
        var lhs = parseShift();
        while( true ) {
            int idx=0;  boolean negate=false;
            // Test for any local nodes made, and "keep" lhs during peepholes
            if( false ) ;
            else if( match("==") ) { idx=2;  lhs = new BoolNode.EQ(lhs, null); }
            else if( match("!=") ) { idx=2;  lhs = new BoolNode.EQ(lhs, null); negate=true; }
            else if( match("<=") ) { idx=2;  lhs = new BoolNode.LE(lhs, null); }
            else if( match("<" ) ) { idx=2;  lhs = new BoolNode.LT(lhs, null); }
            else if( match(">=") ) { idx=1;  lhs = new BoolNode.LE(null, lhs); }
            else if( match(">" ) ) { idx=1;  lhs = new BoolNode.LT(null, lhs); }
            else break;
            // Peepholes can fire, but lhs is already "hooked", kept alive
            lhs.setDef(idx,parseShift());
            lhs = peep(lhs.widen());
            if( negate )        // Extra negate for !=
                lhs = peep(new NotNode(lhs));
        }
        return lhs;
    }

    /**
     * Parse an additive expression
     *
     * <pre>
     *     shiftExpr : additiveExpr (('<<' | '>>' | '>>>') additiveExpr)*
     * </pre>
     * @return a shift expression {@link Node}, never {@code null}
     */
    private Node parseShift() {
        Node lhs = parseAddition();
        while( true ) {
            if( false ) ;
            else if( match("<<") ) lhs = new ShlNode(lhs,null);
            else if( match(">>>")) lhs = new ShrNode(lhs,null);
            else if( match(">>") ) lhs = new SarNode(lhs,null);
            else break;
            lhs.setDef(2,parseAddition());
            lhs = peep(lhs.widen());
        }
        return lhs;
    }

    /**
     * Parse an additive expression
     *
     * <pre>
     *     additiveExpr : multiplicativeExpr (('+' | '-') multiplicativeExpr)*
     * </pre>
     * @return an add expression {@link Node}, never {@code null}
     */
    private Node parseAddition() {
        Node lhs = parseMultiplication();
        while( true ) {
            if( false ) ;
            else if( match("+") ) lhs = new AddNode(lhs,null);
            else if( match("-") ) lhs = new SubNode(lhs,null);
            else break;
            lhs.setDef(2,parseMultiplication());
            lhs = peep(lhs.widen());
        }
        return lhs;
    }

    /**
     * Parse an multiplicativeExpr expression
     *
     * <pre>
     *     multiplicativeExpr : unaryExpr (('*' | '/') unaryExpr)*
     * </pre>
     * @return a multiply expression {@link Node}, never {@code null}
     */
    private Node parseMultiplication() {
        var lhs = parseUnary();  boolean mul;
        while( true ) {
            if( false ) ;
            else if( match("*") ) lhs = new MulNode(lhs,null);
            else if( match("/") ) lhs = new DivNode(lhs,null);
            else break;
            lhs.setDef(2,parseUnary());
            lhs = peep(lhs.widen());
        }
        return lhs;
    }

    /**
     * Parse a unary minus expression.
     *
     * <pre>
     *     unaryExpr : ('-') unaryExpr | '!') unaryExpr | postfixExpr | primaryExpr | '--' Id | '++' Id
     * </pre>
     * @return a unary expression {@link Node}, never {@code null}
     */
    private Node parseUnary() {
        // Pre-dec/pre-inc
        int old = pos();
        if( match("--") || match("++") ) {
            int delta = _lexer.peek(-1)=='+' ? 1 : -1; // Pre vs post
            String name = _lexer.matchId();
            if( name!=null ) {
                ScopeMinNode.Var n = _scope.lookup(name);
                if( n != null ) {
                    if( n._final )
                        throw error("Cannot reassign final '"+n._name+"'");
                    Node expr = zsMask(peep(new AddNode(_scope.in(n),con(delta))),n.type());
                    _scope.update(n,expr);
                    return expr;
                }
            }
            // Reset, try again
            pos(old);
        }
        if (match("-")) return peep(new MinusNode(parseUnary()).widen());
        if (match("!")) return peep(new   NotNode(parseUnary()));
        return parsePostfix(parsePrimary());
    }

    /**
     * Parse a primary expression:
     *
     * <pre>
     *     primaryExpr : integerLiteral | true | false | null | new Type | '(' expression ')' | Id['++','--']
     * </pre>
     * @return a primary {@link Node}, never {@code null}
     */
    private Node parsePrimary() {
        if( _lexer.isNumber(_lexer.peek()) ) return parseLiteral();
        if( matchx("true" ) ) return con(1);
        if( matchx("false") ) return ZERO;
        if( matchx("null" ) ) return con(TypeMemPtr.NULLPTR);
        if( match("(") ) return require(parseExpression(), ")");
        if( matchx("new"  ) ) return alloc();
        // Expect an identifier now
        ScopeMinNode.Var n = requireLookupId("an identifier or expression");
        Node rvalue = _scope.in(n).keep();
        if( matchx("++") || matchx("--") ) {
            if( n._final )
                throw error("Cannot reassign final '"+n._name+"'");
            _scope.update(n,zsMask(peep(new AddNode(rvalue,con( _lexer.peek(-1)=='+' ? 1 : -1))),n.type()));
        }
        return rvalue.unkeep();
    }

    ScopeMinNode.Var requireLookupId(String msg) {
        String id = _lexer.matchId();
        if( id == null || KEYWORDS.contains(id) )
            throw errorSyntax(msg);
        ScopeMinNode.Var n = _scope.lookup(id);
        if( n==null )
            throw error("Undefined name '" + id + "'");
        return n;
    }

    /**
       Parse an allocation
     */
    private Node alloc() {
        Type t = type();
        if( t==null ) throw error("Expected a type");
        // Parse ary[ length_expr ]
        if( match("[") ) {
            Node len = parseExpression();
            if( !(len._type instanceof TypeInteger) )
                throw error("Cannot allocate an array with length "+len._type);
            require("]");
            TypeMemPtr tmp = typeAry(t);
            return newArray(tmp._obj,len);
        }

        if( !(t instanceof TypeMemPtr tmp) )
            throw error("Cannot allocate a "+t.str());

        // Parse new struct { default_initialization }
        StructNode s = INITS.get(tmp._obj._name);
        if( s==null ) throw error("Unknown struct type '" + tmp._obj._name + "'");

        Field[] fs = s._ts._fields;
        // if the object is fully initialized, we can skip a block here.
        // Check for constructor block:
        boolean hasConstructor = match("{");
        Ary<Node> init=s._inputs;  int idx=0;
        if( hasConstructor ) {
            idx = _scope.nIns();
            // Push a scope, and pre-assign all struct fields.
            _scope.push();
            for( int i=0; i<fs.length; i++ )
                _scope.define(fs[i]._fname, fs[i]._type, fs[i]._final, s.in(i));
            // Parse the constructor body
            parseBlock(true);
            require("}");
            init = _scope._inputs;
        }
        // Check that all fields are initialized
        for( int i=idx; i<init.size(); i++ )
            if( init.get(i)._type == Type.TOP )
                throw error("'"+tmp._obj._name+"' is not fully initialized, field '" + fs[i-idx]._fname + "' needs to be set in a constructor");
        Node ptr = newStruct(tmp._obj, con(tmp._obj.offset(fs.length)), idx, init );
        if( hasConstructor )
            _scope.pop();
        return ptr;
    }


    /**
     * Return a NewNode initialized memory.
     * @param obj is the declared type, with GLB fields
     * @param init is a collection of initialized fields
     */
    private Node newStruct( TypeStruct obj, Node size, int idx, Ary<Node> init ) {
        Field[] fs = obj._fields;
        if( fs==null )
            throw error("Unknown struct type '" + obj._name + "'");
        int len = fs.length;
        Node[] ns = new Node[2+len+len];
        ns[0] = ctrl();         // Control in slot 0
        // Total allocated length in bytes
        ns[1] = size;
        // Memory aliases for every field
        for( int i = 0; i < len; i++ )
            ns[2    +i] = memAlias(fs[i]._alias);
        // Initial values for every field
        for( int i = 0; i < len; i++ )
            ns[2+len+i] = init.get(i+idx);
        Node nnn = new NewNode(TypeMemPtr.make(obj), ns).peephole();
        for( int i = 0; i < len; i++ )
            memAlias(fs[i]._alias, new ProjNode(nnn,i+2,memName(fs[i]._alias)).peephole());
        return new ProjNode(nnn,1,obj._name).peephole();
    }

    private static final Ary<Node> ALTMP = new Ary<>(Node.class);
    private Node newArray(TypeStruct ary, Node len) {
        int base = ary.aryBase ();
        int scale= ary.aryScale();
        Node size = peep(new AddNode(con(base),peep(new ShlNode(len.keep(),con(scale)))));
        ALTMP.clear();  ALTMP.add(len.unkeep()); ALTMP.add(con(ary._fields[1]._type.makeInit()));
        return newStruct(ary,size,0,ALTMP);
    }

    // We set up memory aliases by inserting special vars in the scope these
    // variables are prefixed by $ so they cannot be referenced in Simple code.
    // Using vars has the benefit that all the existing machinery of scoping
    // and phis work as expected
    private Node memAlias(int alias         ) { return _scope.mem(alias    ); }
    private void memAlias(int alias, Node st) {        _scope.mem(alias, st); }
    public static String memName(int alias) { return ("$"+alias).intern(); }

    /**
     * Parse postfix expression. For now this is just a field
     * expression, but in future could be array index too.
     *
     * <pre>
     *     expr ('.' IDENTIFIER)* [ = expr ]
     *     expr #
     *     expr ('[' expr ']')* = [ = expr ]
     * </pre>
     */
    private Node parsePostfix(Node expr) {
        String name = null;
        if( match(".") )      name = requireId().intern();
        else if( match("#") ) name = "#";
        else if( match("[") ) name = "[]";
        else return expr;       // No postfix

        // Sanity check expr for being a reference
        if( !(expr._type instanceof TypeMemPtr ptr) )
            throw error("Expected reference but got " + expr._type.str());

        // Happens when parsing known dead code, which often has other typing
        // issues.  Since the code is dead, possibly due to inlining, lets not
        // spoil the user experience with error messages.
        if( ctrl()._type==Type.XCONTROL )
            // Exit out via parsing the trailing expression
            return matchOpx('=','=') ? parseExpression() : parsePostfix(con(Type.TOP));

        // Sanity check field name for existing
        TypeMemPtr tmp = (TypeMemPtr)TYPES.get(ptr._obj._name);
        if( tmp == null ) throw error("Accessing unknown field '" + name + "' from '" + ptr + "'");
        TypeStruct base = tmp._obj;
        int fidx = base.find(name);
        if( fidx == -1 ) throw error("Accessing unknown field '" + name + "' from '" + ptr.str() + "'");

        // Get field type and layout offset from base type and field index fidx
        Field f = base._fields[fidx];  // Field from field index

        Node off = (name.equals("[]")       // If field is an array body
            // Array index math
            ? peep(new AddNode(con(base.aryBase()),peep(new ShlNode(require(parseExpression(),"]"),con(base.aryScale())))))
            // Struct field offsets are hardwired
            : con(base.offset(fidx))).keep();

        // Disambiguate "obj.fld==x" boolean test from "obj.fld=x" field assignment
        if( matchOpx('=','=') ) {
            Node val = parseExpression();
            // Auto-truncate when storing to narrow fields
            val = zsMask(val,f._type).keep();
            Node st = new StoreNode(name, f._alias, f._type, memAlias(f._alias), expr, off.unkeep(), val, false);
            // Arrays include control, as a proxy for a safety range check.
            // Structs don't need this; they only need a NPE check which is
            // done via the type system.
            if( base.isAry() )  st.setDef(0,ctrl());
            memAlias(f._alias, st.peephole());
            return val.unkeep();        // "obj.a = expr" returns the expression while updating memory
        }

        Node load = new LoadNode(name, f._alias, f._type.glb(), memAlias(f._alias), expr.keep(), off);
        // Arrays include control, as a proxy for a safety range check
        // Structs don't need this; they only need a NPE check which is
        // done via the type system.
        if( base.isAry() ) load.setDef(0,ctrl());
        load = peep(load);

        // ary[idx]++ or ptr.fld++
        if( matchx("++") || matchx("--") ) {
            if( f._final && !f._fname.equals("[]") )
                throw error("Cannot reassign final '"+f._fname+"'");
            Node inc = peep(new AddNode(load,con( _lexer.peek(-1)=='+' ? 1 : -1)));
            Node val = zsMask(inc,f._type);
            Node st = new StoreNode(name, f._alias, f._type, memAlias(f._alias), expr.unkeep(), off, val, false);
            // Arrays include control, as a proxy for a safety range check.
            // Structs don't need this; they only need a NPE check which is
            // done via the type system.
            if( base.isAry() )  st.setDef(0,ctrl());
            memAlias(f._alias, peep(st));
            // And use the original loaded value as the result
        } else expr.unkill();
        off.unkill();

        return parsePostfix(load);
    }


    // zero/sign extend.  "i" is limited to either classic unsigned (min==0) or
    // classic signed (min=minus-power-of-2); max=power-of-2-minus-1.
    private Node zsMask(Node val, Type t ) {
        if( !(val._type instanceof TypeInteger tval && t instanceof TypeInteger t0 && !tval.isa(t0)) ) {
            if( !(val._type instanceof TypeFloat tval && t instanceof TypeFloat t0 && !tval.isa(t0)) )
                return val;
            // Float rounding
            return peep(new RoundF32Node(val));
        }
        if( t0._min==0 )        // Unsigned
            return peep(new AndNode(val,con(t0._max)));
        // Signed extension
        int shift = Long.numberOfLeadingZeros(t0._max)-1;
        Node shf = con(shift);
        if( shf._type==TypeInteger.ZERO )
            return val;
        return peep(new SarNode(peep(new ShlNode(val,shf.keep())),shf.unkeep()));
    }


    /**
     * Parse integer literal
     *
     * <pre>
     *     integerLiteral: [1-9][0-9]* | [0]
     *     floatLiteral: [digits].[digits]?[e [digits]]?
     * </pre>
     */
    private ConstantNode parseLiteral() { return con(_lexer.parseNumber()); }
    public static Node con( long con ) { return con(TypeInteger.constant(con));  }
    public static ConstantNode con( Type t ) { return (ConstantNode)new ConstantNode(t).peephole();  }
    public Node peep( Node n ) {
        // Peephole, then improve with lexically scoped guards
        return _scope.upcastGuard(n.peephole());
    }

    //////////////////////////////////
    // Utilities for lexical analysis

    // Return true and skip if "syntax" is next in the stream.
    private boolean match (String syntax) { return _lexer.match (syntax); }
    // Match must be "exact", not be followed by more id letters
    private boolean matchx(String syntax) { return _lexer.matchx(syntax); }
    private boolean matchOpx(char c0, char c1) { return _lexer.matchOpx(c0,c1);  }
    // Return true and do NOT skip if 'ch' is next
    private boolean peek(char ch) { return _lexer.peek(ch); }
    private boolean peekIsId() { return _lexer.peekIsId(); }

    private int pos() { return _lexer._position; }
    private int pos(int pos) {
        int old = _lexer._position;
        _lexer._position = pos;
        return old;
    }
    private Type posT(int pos) { _lexer._position = pos; return null; }

    // Require and return an identifier
    private String requireId() {
        String id = _lexer.matchId();
        if (id != null && !KEYWORDS.contains(id) ) return id;
        throw error("Expected an identifier, found '"+id+"'");
    }



    // Require an exact match
    private Parser require(String syntax) { require(null, syntax); return this; }
    private <N> N require(N n, String syntax) {
        if (match(syntax)) return n;
        throw errorSyntax(syntax);
    }

    RuntimeException errorSyntax(String syntax) {
        return error("Syntax error, expected " + syntax + ": " + _lexer.getAnyNextToken());
    }

    public static RuntimeException error( String errorMessage ) {
        return new RuntimeException(errorMessage);
    }

    ////////////////////////////////////
    // Lexer components

    private static class Lexer {

        // Input buffer; an array of text bytes read from a file or a string
        private final byte[] _input;
        // Tracks current position in input buffer
        private int _position = 0;

        /**
         * Record the source text for lexing
         */
        public Lexer(String source) {
            this(source.getBytes());
        }

        /**
         * Direct from disk file source
         */
        public Lexer(byte[] buf) {
            _input = buf;
        }

        // Very handy in the debugger, shows the unparsed program
        @Override
        public String toString() {
            return new String(_input, _position, _input.length - _position);
        }

        // True if at EOF
        private boolean isEOF() {
            return _position >= _input.length;
        }

        // Peek next character, or report EOF
        private char peek() {
            return isEOF() ? Character.MAX_VALUE   // Special value that causes parsing to terminate
                    : (char) _input[_position];
        }
        // Just crash if misused
        public byte peek(int off) { return _input[_position+off]; }

        private char nextChar() {
            char ch = peek();
            _position++;
            return ch;
        }

        // True if a white space
        private boolean isWhiteSpace() {
            return peek() <= ' '; // Includes all the use space, tab, newline, CR
        }

        /**
         * Return the next non-white-space character
         */
        private void skipWhiteSpace() {
            while( true ) {
                if( isWhiteSpace() ) _position++;
                // Skip // to end of line
                else if( _position+2 < _input.length &&
                         _input[_position  ] == '/' &&
                         _input[_position+1] == '/') {
                    _position += 2;
                    while( !isEOF() && _input[_position] != '\n' ) _position++;
                } else break;
            }
        }

        // Next non-white-space character, or EOF
        public char nextXChar() { skipWhiteSpace(); return nextChar(); }

        // Return true, if we find "syntax" after skipping white space; also
        // then advance the cursor past syntax.
        // Return false otherwise, and do not advance the cursor.
        boolean match(String syntax) {
            skipWhiteSpace();
            int len = syntax.length();
            if (_position + len > _input.length) return false;
            for (int i = 0; i < len; i++)
                if ((char) _input[_position + i] != syntax.charAt(i))
                    return false;
            _position += len;
            return true;
        }

        // Match must be exact and not followed by more ID characters.
        // Prevents identifier "ifxy" from matching an "if" statement.
        boolean matchx(String syntax) {
            if( !match(syntax) ) return false;
            if( !isIdLetter(peek()) ) return true;
            _position -= syntax.length();
            return false;
        }
        boolean matchOpx(char c0, char c1) {
            skipWhiteSpace();
            if( _position+1 >= _input.length || _input[_position]!=c0 || _input[_position+1]==c1 )
                return false;
            _position++;
            return true;
        }

        private boolean peek(char ch) {
            skipWhiteSpace();
            return peek()==ch;
        }

        boolean peekIsId() {
            skipWhiteSpace();
            return isIdStart(peek());
        }

        // Return an identifier or null
        String matchId() {
            return peekIsId() ? parseId() : null;
        }

        // Used for errors
        String getAnyNextToken() {
            if (isEOF()) return "";
            if (isIdStart(peek())) return parseId();
            if (isNumber(peek())) return parseNumberString();
            if (isPunctuation(peek())) return parsePunctuation();
            return String.valueOf(peek());
        }


        boolean isNumber(char ch) {return Character.isDigit(ch);}

        // Return a constant Type, either TypeInteger or TypeFloat
        private Type parseNumber() {
            int old = _position;
            int len = isLongOrDouble();
            if( len > 0 ) {
                if( len > 1 && _input[old]=='0' )
                    throw error("Syntax error: integer values cannot start with '0'");
                return TypeInteger.constant(Long.parseLong(new String(_input,old,len)));
            }
            return TypeFloat.constant(Double.parseDouble(new String(_input,old,-len)));
        }
        private String parseNumberString() {
            int old = _position;
            int len = Math.abs(isLongOrDouble());
            _position += len;
            return new String(_input,old,len);
        }

        // Return +len that ends a long
        // Return -len that ends a double
        private int isLongOrDouble() {
            int old = _position;
            char c;
            while( Character.isDigit(c=nextChar()) ) ;
            if( !(c=='e' || c=='.') )
                return --_position - old;
            while( Character.isDigit(c=nextChar()) || c=='e' || c=='.' ) ;
            return -(--_position - old);
        }

        // First letter of an identifier
        private boolean isIdStart(char ch) {
            return Character.isAlphabetic(ch) || ch == '_';
        }

        // All characters of an identifier, e.g. "_x123"
        private boolean isIdLetter(char ch) {
            return Character.isLetterOrDigit(ch) || ch == '_';
        }

        private String parseId() {
            int start = _position;
            while (isIdLetter(nextChar())) ;
            return new String(_input, start, --_position - start);
        }

        //
        private boolean isPunctuation(char ch) {
            return "=;[]<>()+-/*".indexOf(ch) != -1;
        }

        private String parsePunctuation() {
            int start = _position;
            return new String(_input, start, 1);
        }
    }
}
