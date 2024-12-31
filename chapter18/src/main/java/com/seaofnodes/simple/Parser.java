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
    public static ConstantNode NIL;  // Very common node, cached here
    public static XCtrlNode XCTRL;   // Very common node, cached here

    // Next available memory alias number
    static int ALIAS;

    public StopNode STOP;

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
    FunNode _fun;               // Current function being parsed

    // Mapping from a type name to a Type.  The string name matches
    // `type.str()` call.  No TypeMemPtrs are in here, because Simple does not
    // have C-style '*ptr' references.
    public static HashMap<String, Type> TYPES;

    // Mapping from a type name to the constructor for a Type.
    public final HashMap<String, StructNode> INITS;


    public Parser(String source) { this(source, TypeInteger.BOT); }

    public Parser(String source, TypeInteger arg) {
        Node.reset();
        IterPeeps.reset();
        _lexer = new Lexer(source);
        _scope = new ScopeNode();
        _continueScope = _breakScope = null;
        START = new StartNode(arg);
        STOP = new StopNode(source);
        ZERO  = con(TypeInteger.ZERO).keep();
        NIL  = con(Type.NIL).keep();
        XCTRL= new XCtrlNode().peephole().keep();
        ALIAS = 2; // alias 0 for the control, 1 for memory
        TYPES = defaultTypes();
        INITS = new HashMap<>();
    }

    @Override
    public String toString() { return _lexer.toString(); }

    public static HashMap<String, Type> defaultTypes() {
        return new HashMap<>() {{
            put("bool",TypeInteger.U1 );
            put("byte",TypeInteger.U8 );
            put("f32" ,TypeFloat  .F32);
            put("f64" ,TypeFloat  .F64);
            put("flt" ,TypeFloat  .F64);
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
        //// Enter a new scope for the initial control and arguments
        //_scope.push();
        //Node ctrl = new CProjNode(START, 0, ScopeNode.CTRL).peephole();
        //Node pmem = new  ProjNode(START, 1, ScopeNode.MEM0).peephole();
        //Node arg  = new  ProjNode(START, 2, ScopeNode.ARG0).peephole();
        //ScopeMinNode mem = new ScopeMinNode();
        //mem.addDef(null);
        //mem.addDef(pmem);
        _scope.define(ScopeNode.CTRL, Type.CONTROL   , false, null);
        _scope.define(ScopeNode.MEM0, TypeMem.BOT    , false, null);
        _scope.define(ScopeNode.ARG0, TypeInteger.BOT, false, null);
        //
        //// Call main
        //CallNode main = (CallNode)new CallNode(ctrl,pmem,arg,con(TypeFunPtr.MAIN)).peephole();
        //// Call End
        //CallEndNode cend = (CallEndNode)new CallEndNode(main).peephole();
        //STOP.addDef(new CProjNode(cend,0,ScopeNode.CTRL).peephole());
        //STOP.addDef(new  ProjNode(cend,1,ScopeNode.MEM0).peephole());
        //STOP.addDef(new  ProjNode(cend,2,ScopeNode.ARG0).peephole());

        // Parse whole program, as-if function header "{ int arg -> body }"
        ReturnNode ret = parseFunctionBody("main",TypeFunPtr.MAIN,"arg");
        CodeGen.CODE._linker.put(TypeFunPtr.MAIN,ret.fun());
        //main.peephole();
        //if( cend.peephole() != null )
        //    IterPeeps.addAll(cend._outputs);

        if( !_lexer.isEOF() ) throw _errorSyntax("unexpected");

        // Clean up and reset
        _xScopes.pop();
        _scope.kill();
        for( StructNode init : INITS.values() )
            init.unkeep().kill();
        INITS.clear();
        STOP.peephole();
        JSViewer.show();
        if( show ) showGraph();
        return STOP;
    }

    /**
     *  Parses a function body, assuming the header is parsed.
     */
    private ReturnNode parseFunctionBody(String name, TypeFunPtr sig, String... ids) {
        sig.setName(name);
        FunNode fun = _fun = (FunNode)peep(new FunNode(START,sig,name));

        // Build a multi-exit return point for all function returns
        RegionNode r = new RegionNode(null,null);
        assert r.inProgress();
        PhiNode rmem = new PhiNode(ScopeNode.MEM0,TypeMem.BOT,r,null);
        PhiNode rrez = new PhiNode(ScopeNode.ARG0,Type.BOTTOM,r,null);
        ReturnNode ret = new ReturnNode(r, rmem, rrez, fun);
        fun.setRet(ret);
        assert ret.inProgress();
        STOP.addDef(ret);

        // Pre-call the function from Start, with worse-case arguments.  This
        // represents all the future, yet-to-be-parsed functions calls and
        // external calls.
        _scope.push();
        ctrl(fun);              // Scope control from function
        // Private mem alias tracking per function
        ScopeMinNode mem = new ScopeMinNode(true);
        mem.addDef(null);       // Alias#0
        mem.addDef(new ParmNode(ScopeNode.MEM0,1,TypeMem.BOT,fun,con(TypeMem.BOT)).peephole()); // All aliases
        _scope.mem(mem);
        // All args, "as-if" called externally
        for( int i=0; i<ids.length; i++ ) {
            Type t = sig.arg(i);
            assert t==t.glb();  // Expect args declared type not be lifted
            _scope.define(ids[i], t, false, new ParmNode(ids[i],i+2,t,fun,con(t)).peephole());
        }

        // Parse the body
        Node last=ZERO;
        while (!peek('}') && !_lexer.isEOF())
            last = parseStatement();

        // Last expression is the return
        if( ctrl()._type==Type.CONTROL )
            STOP.addReturn(ctrl(), _scope.mem().merge(), last, fun);

        // Function scope ends
        _scope.pop();
        _fun = null;

        // Pop off the inProgress node on the multi-exit Region merge
        assert r.inProgress();
        r   ._inputs.pop();
        rmem._inputs.pop();
        rrez._inputs.pop();
        assert !r.inProgress();

        // Force peeps, which have been avoided due to inProgress
        ret.setDef(1,rmem.peephole());
        ret.setDef(2,rrez.peephole());
        ret.setDef(0,r.peephole());
        return (ReturnNode)ret.peephole();
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
        Node last = ZERO;
        while (!peek('}') && !_lexer.isEOF())
            last = parseStatement();
        // Exit scope
        last.keep();
        _scope.pop();
        return last.unkeep();
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
        else if (matchx("if")      ) return parseIf();
        else if (matchx("while")   ) return parseWhile();
        else if (matchx("for")     ) return parseFor();
        else if (matchx("break")   ) return parseBreak();
        else if (matchx("continue")) return parseContinue();
        else if (matchx("struct")  ) return parseStruct();
        else if (matchx("#showGraph")) return require(showGraph(),";");
        else if (matchx(";")       ) return null; // Empty statement
        // Break ambiguity around leading function types and starting a block
        else if (peek('{') && !isTypeFun() ) {
            match("{");
            return require(parseBlock(false),"}");
        }
        // Declaration or normal assignment/expression
        else return parseDeclarationStatement();
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
        //     while( test ) {
        //         body;
        //         next;
        //     }
        // }
        require("(");
        _scope.push();          // Scope for the index variables
        if( !match(";") )       // Can be empty init "for(;test;next) body"
            parseDeclarationStatement(); // Non-empty init
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
        var pred = peek(';') ? con(1) : parseAsgn();
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
        Node expr = parseStatement().keep();       // Parse loop body
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
              parseAsgn();
            if( pos() != nextEnd )
                throw errorSyntax( "Unexpected code after expression" );
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
        return peep(expr.unkeep());
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

    private void checkLoopActive() { if (_breakScope == null) throw error("No active loop for a break or continue"); }

    private Node parseContinue() { checkLoopActive(); _continueScope = require(jumpTo( _continueScope ),";"); return ZERO; }
    private Node parseBreak   () {
        checkLoopActive();
        // At the time of the break, and loop-exit conditions are only valid if
        // they are ALSO valid at the break.  It is the intersection of
        // conditions here, not the union.
        _breakScope.removeGuards(_breakScope.ctrl());
        _breakScope = require(jumpTo(_breakScope ),";");
        _breakScope.addGuards(_breakScope.ctrl(), null, false);
        return ZERO;
    }

    // Look for an unbalanced `)`, skipping balanced
    private void skipAsgn() {
        int paren=0;
        while( true )
            // Next X char handles skipping complex comments
            switch( _lexer.nextXChar() ) {
            case Character.MAX_VALUE:
                throw Utils.TODO();
            case ')':
                if( --paren<0 ) {
                    posT( pos() - 1 );
                    return; // Leave the `)` behind
                }
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
        var pred = require(parseAsgn(), ")");
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
        Node lhs = (stmt ? parseStatement() : parseAsgn()).keep(); // Parse true-side
        _scope.removeGuards(ifT);

        ScopeNode tScope = _scope;

        // Parse the false side
        _scope = fScope;        // Restore scope, then parse else block if any
        ctrl(ifF.unkeep());     // Ctrl token is now set to ifFalse projection
        // Up-cast predicate, even if not else clause, because predicate can
        // remain true if the true clause exits: `if( !ptr ) return 0; return ptr.fld;`
        _scope.addGuards(ifF,pred,true);
        boolean doRHS = match(fside);
        Node rhs = (doRHS
            ? (stmt ? parseStatement() : parseAsgn())
            : con(lhs._type.makeZero())).keep();
        _scope.removeGuards(ifF);
        if( doRHS )
            fScope = _scope;
        pred.unkeep();

        // Check for `if(pred) int x=17;`
        if( tScope.nIns() != ndefs || fScope.nIns() != ndefs )
            throw error("Cannot define a new name on one arm of an if");

        // Check the trinary widening int/flt
        if( !stmt ) {
            rhs = widenInt( rhs.unkeep(), lhs._type ).keep();
            lhs = widenInt( lhs.unkeep(), rhs._type ).keep();
        }

        // Merge results
        _scope = tScope;
        _xScopes.pop();       // Discard pushed from graph display

        RegionNode r = ctrl(tScope.mergeScopes(fScope));
        Node ret = peep(new PhiNode("",lhs._type.meet(rhs._type),r,lhs.unkeep(),rhs.unkeep()));
        // Immediately fail e.g. `arg ? 7 : ptr`
        if( !stmt && ret.err() !=null )
            throw error(ret.err());
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
        var expr = require(parseAsgn(), ";");
        // Need default memory, since it can be lazy, need to force
        // a non-lazy Phi
        STOP.addReturn(ctrl(), _scope.mem().merge(), expr, _fun);
        ctrl(XCTRL);            // Kill control
        return expr;
    }

    /**
     * Dumps out the node graph
     * @return {@code null}
     */
    Node showGraph() {
        System.out.println(new GraphVisualizer().generateDotOutput(STOP,_scope,_xScopes));
        return null;
    }

    /** Parse: [name '='] expr
     */
    private Node parseAsgn() {
        int old = pos();
        String name = _lexer.matchId();
        // Just a plain expression, no assignment.
        // Distinguish `var==expr` from `var=expr`
        if( name==null || KEYWORDS.contains(name) || !matchOpx('=','=') )
            {  pos(old);  return parseExpression();  }

        // Parse assignment expression
        Node expr = parseAsgn();

        // Final variable to update
        ScopeMinNode.Var def = _scope.lookup(name);
        if( def==null )
            throw error("Undefined name '" + name + "'");

        // TOP fields are for late-initialized fields; these have never
        // been written to, and this must be the final write.  Other writes
        // outside the constructor need to check the final bit.
        if( _scope.in(def._idx)._type!=Type.TOP && def._final && !_scope.inCon() )
            throw error("Cannot reassign final '"+name+"'");

        // Lift expression, based on type
        Node lift = liftExpr(expr.keep(), def.type(), def._final);
        // Update
        _scope.update(name,lift);
        // Return un-lifted expr
        return expr.unkeep();
    }

    // Make finals deep; widen ints to floats; narrow wide int types.
    // Early error if types do not match variable.
    private Node liftExpr( Node expr, Type t, boolean xfinal ) {
        assert !(expr._type instanceof TypeMemPtr tmp) || !tmp.isFRef();
        // Final is deep on ptrs
        if( xfinal && t instanceof TypeMemPtr tmp ) {
            t = tmp.makeRO();
            expr = peep(new ReadOnlyNode(expr));
        }
        // Auto-widen int to float
        expr = widenInt( expr, t );
        // Auto-narrow wide ints to narrow ints
        expr = zsMask(expr,t);
        // Type is sane
        if( !expr._type.isa(t) )
            throw error("Type " + expr._type.str() + " is not of declared type " + t.str());
        return expr;
    }

    private Node widenInt( Node expr, Type t ) {
        return (expr._type instanceof TypeInteger || expr._type==Type.NIL) && t instanceof TypeFloat
            ? peep(new ToFloatNode(expr)) : expr;
    }

    /**
     * Parse declaration or expression statement
     * declStmt = type var['=' exprAsgn][, var['=' exprAsgn]]* ';' | exprAsgn ';'
     * <p>
     * exprAsgn = var '=' exprAsgn | expr
     */
    private Node parseDeclarationStatement() {
        Type t = type();
        if( t == null )
            return require(parseAsgn(),";");

        // now parse var['=' asgnexpr] in a loop
        Node n = parseDeclaration(t);
        while( match(",") )
            n = parseDeclaration(t);
        return require(n,";");
    }

    /** Parse final: [!]var['=' asgn]
     */
    private Node parseDeclaration(Type t) {
        assert t!=null;
        // Has var/val instead of a user-declared type
        boolean inferType = t==Type.TOP || t==Type.BOTTOM;
        boolean hasBang = match("!");
        String name = requireId();

        // Optional initializing expression follows
        boolean xfinal = false;
        Node expr;
        if( match("=") ) {
            expr = parseAsgn();
            // `val` is always final
            xfinal = (t==Type.TOP) ||
                // var is always not-final, final if no Bang AND TMP since primitives are not-final by default
                (t!=Type.BOTTOM && !hasBang && (t instanceof TypeMemPtr));
            // var/val, then type comes from expression
            if( inferType ) {
                if( expr._type==Type.NIL )
                    throw error("a not-null/non-zero expression");
                t = expr._type.glb();
            }

        } else {
            if( inferType && !_scope.inCon() )
                throw errorSyntax("=expression");
            // Initial value for uninitialized struct fields.
            expr = t instanceof TypeMemPtr tn
                // Nullable pointers get a NIL; not-null get a TOP which
                // signals that they *must* be initialized in the constructor.
                ? (tn.nullable() ? NIL : con(Type.TOP))
                // Bottom signals type inference: they must be initialized in
                // the constructor and that's when we'll discover the type.
                // Otherwise, its an Integer and use 0.
                : (t==Type.BOTTOM ? con(t) : (t instanceof TypeInteger ? ZERO : con(TypeFloat.FZERO)));
        }

        // Lift expression, based on type
        Node lift = liftExpr(expr, t, xfinal);
        if( xfinal && t instanceof TypeMemPtr tmp )
            t = tmp.makeRO();

        // Define a new name,
        if( !_scope.define(name,t,xfinal,lift) )
            throw error("Redefining name '" + name + "'");
        return lift;
    }



    /**
     * Parse a struct declaration, and return the following statement.
     * Only allowed in top level scope.
     * Structs cannot be redefined.
     *
     * @return The statement following the struct
     */
    private Node parseStruct() {
        if (_xScopes.size() > 1) throw error("struct declarations can only appear in top level scope");
        String typeName = requireId();
        Type t = TYPES.get(typeName);
        if( t!=null && !(t instanceof TypeMemPtr tmp && tmp.isFRef() ) )
            throw error("struct '" + typeName + "' cannot be redefined");

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
        return ZERO;
    }


    // Parse and return a type or null.  Valid types always are followed by an
    // 'id' which the caller must parse.  This lets us distinguish forward ref
    // types (which ARE valid here) from local vars in an (optional) forward
    // ref type position.

    // t = int|i8|i16|i32|i64|u8|u16|u32|u64|byte|bool | flt|f32|f64 | val | var | struct[?]
    private Type type() {
        int old1 = pos();
        // Only type with a leading `{` is a function pointer...
        if( peek('{') ) return typeFunPtr();

        // Otherwise you get a type name
        String tname = _lexer.matchId();
        if( tname==null ) return null;

        // Convert the type name to a type.
        Type t0 = TYPES.get(tname);
        // No new types as keywords
        if( t0 == null && KEYWORDS.contains(tname) )
            return posT(old1);
        if( t0 == Type.BOTTOM || t0 == Type.TOP ) return t0; // var/val type inference
        Type t1 = t0 == null ? TypeMemPtr.make(TypeStruct.makeFRef(tname)) :t0; // Null: assume a forward ref type
        // Nest arrays and '?' as needed
        Type t2 = t1;
        while( true ) {
            if( match("?") ) {
                if( !(t2 instanceof TypeMemPtr tmp) )
                    throw error("Type "+t0+" cannot be null");
                if( tmp.nullable() ) throw error("Type "+t2+" already allows null");
                t2 = tmp.makeNullable();
            } else if( match("[]") ) {
                t2 = typeAry(t2);
            } else
                break;
        }

        // Check no forward ref
        if( t0 != null ) return t2;
        // Check valid forward ref, after parsing all the type extra bits.
        // Cannot check earlier, because cannot find required 'id' until after "[]?" syntax
        int old2 = pos();
        String id = _lexer.matchId();
        pos(old2);              // Reset lexer to reparse
        if( id==null || _scope.lookup(id)!=null )
            return posT(old1);  // Reset lexer to reparse
        // Yes a forward ref, so declare it
        TYPES.put(tname,t1);
        return t2;
    }

    // Make an array type of t
    private TypeMemPtr typeAry( Type t ) {
        if( t instanceof TypeMemPtr tmp && tmp.notNull()  )
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

    // A function type is `{ type... -> type }` or `{ type }`.
    private Type typeFunPtr() {
        int old = pos();        // Record lexer position
        match("{");             // Skip already-peeked '{'
        Type t0 = type();       // Either return or first arg
        if( t0==null ) return posT(old); // Not a function
        if( match("}") )                 // No-arg function { -> type }
            throw Utils.TODO();
        Ary<Type> ts = new Ary<>(Type.class);
        ts.push(t0);            // First argument
        while( true ) {
            if( match("->") ) { // End of arguments, parse return
                Type ret = type();
                if( ret==null || !match("}") )
                    return posT(old); // Not a function
                return TypeFunPtr.make(match("?"),TypeTuple.make(ts.asAry()),ret);
            }
            Type t1 = type();
            if( t1==null ) return posT(old); // Not a function
            ts.push(t1);
        }
    }

    // True if a TypeFunPtr, without advancing parser
    private boolean isTypeFun() {
        int old = pos();
        if( typeFunPtr()==null ) return false;
        pos(old);
        return true;
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
            if( lhs.err() != null )
                throw error(lhs.err());
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
        var lhs = parseUnary();
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
                if( n != null && !(n.type() instanceof TypeMemPtr) ) {
                    if( n._final )
                        throw error("Cannot reassign final '"+n._name+"'");
                    Node expr = n.type() instanceof TypeFloat
                        ?        peep(new AddFNode(_scope.in(n),con(TypeFloat.constant(-1))))
                        : zsMask(peep(new  AddNode(_scope.in(n),con(delta))),n.type());
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
        if( matchx("null" ) ) return NIL;
        if( match ("("    ) ) return require(parseAsgn(), ")");
        if( matchx("new"  ) ) return alloc();
        if( match ("{"    ) ) return func();
        // Expect an identifier now
        ScopeMinNode.Var n = requireLookupId("an identifier or expression");
        Node rvalue = _scope.in(n);
        if( rvalue._type == Type.BOTTOM )
            throw error("Cannot read uninitialized field '"+n._name+"'");
        // Check for assign-update, x += e0;
        char ch = _lexer.matchOperAssign();
        if( ch==0  ) return rvalue;

        // the rvalue is an l-value now; it will be updated
        Node lhs = rvalue.keep();
        if( n._final )
            throw error("Cannot reassign final '"+n._name+"'");
        // RHS of the update
        Node rhs =
            (byte)ch ==  1 ? con( 1) : // var++
            (byte)ch == -1 ? con(-1) : // var--
            parseAsgn();         // var op= rhs
        // Widen RHS int to a RHS float
        rhs = widenInt(rhs,n.type());
        // Complain a RHS float into a LHS int
        if( !(rhs._type instanceof TypeInteger) && !rhs._type.isa(n.type()) )
            throw error("Type " + rhs._type.str() + " is not of declared type " + n.type().str());

        Node op = switch(ch) {
        case 1, (char)-1,
             '+' -> new AddNode(lhs,rhs);
        case '-' -> new SubNode(lhs,rhs);
        case '*' -> new MulNode(lhs,rhs);
        case '/' -> new DivNode(lhs,rhs);
        default  -> throw Utils.TODO();
        };

        op = zsMask(peep(op.widen()),n.type());
        _scope.update(n,op);
        // Return pre-value (x+=1) or post-value (x++)
        lhs.unkeep();
        return (byte)ch== 1 || (byte)ch== -1 ? lhs : op;
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
            Node len = parseAsgn();
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
                _scope.define(fs[i]._fname, fs[i]._type, fs[i]._final, s.in(i)._type==Type.TOP ? con(Type.BOTTOM) : s.in(i));
            // Parse the constructor body
            parseBlock(true);
            require("}");
            init = _scope._inputs;
        }
        // Check that all fields are initialized
        for( int i=idx; i<init.size(); i++ )
            if( init.at(i)._type == Type.TOP || init.at(i)._type == Type.BOTTOM )
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
        ALTMP.clear();  ALTMP.add(len.unkeep()); ALTMP.add(con(ary._fields[1]._type.makeZero()));
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
     * Parse postfix expression; this can be a field expression, an array
     * lookup or a postfix operator like '#'
     *
     * <pre>
     *     expr ('.' IDENTIFIER)* [ = expr ]
     *     expr #
     *     expr ('[' expr ']')* = [ = expr ]
     * </pre>
     */
    private Node parsePostfix(Node expr) {
        String name = null;
        if( match(".") )      name = requireId();
        else if( match("#") ) name = "#";
        else if( match("[") ) name = "[]";
        else return expr;       // No postfix

        // Happens when parsing known dead code, which often has other typing
        // issues.  Since the code is dead, possibly due to inlining, lets not
        // spoil the user experience with error messages.
        //if( ctrl()._type==Type.XCONTROL ) {
        //    if( expr.isUnused() ) expr.kill(); // Losing last use of expr
        //    // Exit out via parsing the trailing expression
        //    return matchOpx( '=', '=' ) ? parseAsgn() : parsePostfix( con( Type.TOP ) );
        //}

        //
        if( expr._type==Type.NIL )
            throw error("Accessing unknown field '" + name + "' from 'null'");

        // Sanity check expr for being a reference
        if( !(expr._type instanceof TypeMemPtr ptr) )
            throw error("Expected reference but got " + expr._type.str());

        // Sanity check field name for existing
        TypeMemPtr tmp = (TypeMemPtr)TYPES.get(ptr._obj._name);
        if( tmp == null ) throw error("Accessing unknown field '" + name + "' from '" + ptr + "'");
        TypeStruct base = tmp._obj;
        int fidx = base.find(name);
        if( fidx == -1 ) throw error("Accessing unknown field '" + name + "' from '" + ptr.str() + "'");

        // Get field type and layout offset from base type and field index fidx
        Field f = base._fields[fidx];  // Field from field index
        Type tf = f._type;
        if( tf instanceof TypeMemPtr ftmp && ftmp.isFRef() )
            tf = ftmp.makeFrom(((TypeMemPtr)(TYPES.get(ftmp._obj._name)))._obj);

        // Field offset; fixed for structs, computed for arrays
        Node off = (name.equals("[]")       // If field is an array body
            // Array index math
            ? peep(new AddNode(con(base.aryBase()),peep(new ShlNode(require(parseAsgn(),"]"),con(base.aryScale())))))
            // Struct field offsets are hardwired
            : con(base.offset(fidx))).keep();

        // Disambiguate "obj.fld==x" boolean test from "obj.fld=x" field assignment
        if( matchOpx('=','=') ) {
            Node val = parseAsgn().keep();
            Node lift = liftExpr( val, tf, f._final );

            Node st = new StoreNode(name, f._alias, tf, memAlias(f._alias), expr, off.unkeep(), lift, false);
            // Arrays include control, as a proxy for a safety range check.
            // Structs don't need this; they only need a NPE check which is
            // done via the type system.
            if( base.isAry() )  st.setDef(0,ctrl());
            memAlias(f._alias, st.peephole());
            return val.unkeep();        // "obj.a = expr" returns the expression while updating memory
        }

        Node load = new LoadNode(name, f._alias, tf.glb(), memAlias(f._alias), expr.keep(), off);
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
            Node val = zsMask(inc,tf);
            Node st = new StoreNode(name, f._alias, tf, memAlias(f._alias), expr.unkeep(), off, val, false);
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
     * Parse a function; the leading '{' is already parsed.
     *
     * <pre>
     *     { [type arg,]* -> expr }
     *     { expr } // The no-argument function
     * </pre>
     */
    private Node func() {
        int old = pos();        // Record lexer position
        Ary<Type> ts = new Ary<>(Type.class);
        Ary<String> ids = new Ary<>(String.class);
        while( true ) {
            Type t = type();    // Arg type
            if( t==null ) break;
            String id = requireId();
            ts .push(t );       // Push type/arg pairs
            ids.push(id);
            match(",");
        }
        require("->");
        // Make a concrete function type, with a fidx
        TypeFunPtr tfp = TypeFunPtr.makeFun(TypeTuple.make(ts.asAry()),Type.BOTTOM);
        return parseFunctionBody(null,tfp,ids.asAry());
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
    public static Node con( long con ) { return con==0 ? ZERO : con(TypeInteger.constant(con));  }
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

    public int pos() { return _lexer._position; }
    private int pos(int pos) {
        int old = _lexer._position;
        _lexer._position = pos;
        return old;
    }
    private Type posT(int pos) { _lexer._position = pos; return null; }

    // Require and return an identifier
    private String requireId() {
        String id = _lexer.matchId();
        if (id != null && !KEYWORDS.contains(id) ) return id.intern();
        throw error("Expected an identifier, found '"+id+"'");
    }

    private String matchId() {
        int old = pos();
        String id = _lexer.matchId();
        if( id==null ) return null;
        if( !KEYWORDS.contains(id) ) return id;
        pos(old);
        return null;
    }

    // Require an exact match
    private Parser require(String syntax) { require(null, syntax); return this; }
    private <N> N require(N n, String syntax) {
        if (match(syntax)) return n;
        throw errorSyntax(syntax);
    }

    RuntimeException errorSyntax(String syntax) { return _errorSyntax("expected "+syntax);  }
    private RuntimeException _errorSyntax(String msg) {
        return _lexer.error("Syntax error, "+msg+": " + _lexer.getAnyNextToken());
    }
    RuntimeException error(String msg) { return _lexer.error(msg); }

    ////////////////////////////////////
    // Lexer components

    private static class Lexer {

        // Input buffer; an array of text bytes read from a file or a string
        private final byte[] _input;
        // Tracks current position in input buffer
        private int _position = 0;

        //
        private int _line_number = 1;
        // Start of current line
        private int _line_start = 0;

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

        private void inc() {
            if( _position++ < _input.length && _input[_position-1] == '\n' ) {
                _line_number++;
                _line_start = _position;
            }
        }
        // Does not honor LF, so caller must roll back position on a LF
        private char nextChar() {
            char ch = peek();
            inc();
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
                if( isWhiteSpace() ) inc();
                // Skip // to end of line
                else if( _position+2 < _input.length &&
                         _input[_position  ] == '/' &&
                         _input[_position+1] == '/') {
                    inc(); inc();
                    while( !isEOF() && _input[_position] != '\n' ) inc();
                } else break;
            }
        }

        // Next non-white-space character, or EOF
        public char nextXChar() { skipWhiteSpace(); return nextChar(); }

        // Return true, if we find "syntax" after skipping white space; also
        // then advance the cursor past syntax.
        // Return false otherwise, and do not advance the cursor.
        boolean match(String syntax) {
            assert syntax.indexOf('\n')==-1; // No newlines in match
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
            inc();
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
                long i = Long.parseLong(new String(_input,old,len));
                return TypeInteger.constant(i);
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
            return "=;[]<>()+-/*&|^".indexOf(ch) != -1;
        }

        private String parsePunctuation() {
            int start = _position;
            return new String(_input, start, 1);
        }

        // Next oper= character, or 0.
        // As a convenience, mark "++" as a char 1 and "--" as char -1 (65535)
        public char matchOperAssign() {
            skipWhiteSpace();
            if( _position+2 >= _input.length ) return 0;
            char ch0 = (char)_input[_position];
            if( "+-/*&|^".indexOf(ch0) == -1 ) return 0;
            char ch1 = (char)_input[_position+1];
            if(               ch1 == '=' ) { _position += 2; return ch0; }
            if( isIdLetter((char)_input[_position+2]) ) return 0;
            if( ch0 == '+' && ch1 == '+' ) { _position += 2; return (char) 1; }
            if( ch0 == '-' && ch1 == '-' ) { _position += 2; return (char)-1; }
            return 0;
        }

        public RuntimeException error( String errorMessage ) {
            // file:line:charoff err
            //String msg = "src:"+_line_number+":"+(_position-_line_start)+" "+errorMessage;
            return new ParserException(errorMessage);
        }

    }
    public static class ParserException extends RuntimeException {
        ParserException(String msg) { super(msg); }
    }
}
