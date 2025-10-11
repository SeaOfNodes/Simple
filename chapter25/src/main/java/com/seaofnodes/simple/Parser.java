package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.GraphVisualizer;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.Utils;
import com.seaofnodes.simple.node.ScopeNode.Kind;
import java.util.*;

/**
 * The Parser converts a Simple source program to the Sea of Nodes intermediate
 * representation directly in one pass.  There is no intermediate Abstract
 * Syntax Tree structure.
 * <p>
 * This is a simple recursive descent parser. All lexical analysis is done here as well.
 */
public class Parser {

    public static ConstantNode ZERO; // Very common node, cached here
    public static ConstantNode NIL;  // Very common node, cached here
    public static XCtrlNode XCTRL;   // Very common node, cached here

    // Compile driver
    public final CodeGen _code;

    // The Lexer.  Thin wrapper over a byte[] buffer with a cursor.
    private Lexer _lexer;

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
    public static final HashSet<String> KEYWORDS = new HashSet<>(){{
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
    // `type.str()` call.
    public static HashMap<String, Type> TYPES = defaultTypes();

    // Mapping from a type name to the constructor for a Type.
    public final HashMap<String, StructNode> INITS;


    public Parser(CodeGen code, TypeInteger arg) {
        _code = code;
        _scope = new ScopeNode();
        _continueScope = _breakScope = null;
        ZERO  = con(TypeInteger.ZERO).keep();
        NIL  = con(Type.NIL).keep();
        XCTRL= new XCtrlNode().peephole().keep();
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
    public Node f(int nid) { return _code.f(nid); }

    private Node ctrl() { return _scope.ctrl(); }
    private <N extends Node> N ctrl(N n) { return _scope.ctrl(n); }

    public void parse() {

        _scope.define(ScopeNode.CTRL, Type.CONTROL   , false, null, _lexer);
        _scope.define(ScopeNode.MEM0, TypeMem.BOT    , false, null, _lexer);
        _scope.define(ScopeNode.ARG0, TypeInteger.BOT, false, null, _lexer);

        ctrl(XCTRL);
        _scope.mem(new MemMergeNode(false));

        // Parse the sys import
        _lexer = new Lexer(sys.SYS);
        while( !_lexer.isEOF() ) {
            parseStatement();
            _lexer.skipWhiteSpace();
        }

        // Reset lexer for program text
        _lexer = new Lexer(_code._src);
        _xScopes.push(_scope);

        // Parse whole program, as-if function header "{ int arg -> body }"
        parseFunctionBody( _code._main,loc(),"arg");

        // Kill an empty default main.  Keep only if it was explicitly defined
        // (programmer asked for a "main") or it has stuff (i.e. beginner
        // default main).
        FunNode main = _code.link(_code._main);
        StopNode stop = _code._stop;
        // Gather an explicit main
        FunNode xmain = null;
        for( Node n : stop._inputs )
            if( n instanceof ReturnNode ret && "main".equals(ret.fun()._name) )
                if( xmain != null ) throw Utils.TODO();
                else xmain = ret.fun();

        if( main.ret().expr()._type==Type.TOP && main.uctrl()==null ) {
            // Kill an empty default main; so it does not attempt to put a
            // "main" in any final ELF file
            main.setDef(1,XCTRL); // Delete default start input
            stop.delDef(stop._inputs.find(main.ret()));
            if( xmain != null )
                _code.setMain(xmain);

        } else {
            // We have a non-empty default main.
            if( xmain != null )
                // Found an explicit "main" AND we have a default "main"
                throw error("Cannot define both an explicit main and a default main");
            main._name = "main";
        }

        if( !_lexer.isEOF() ) throw _errorSyntax("unexpected");

        // Clean up and reset
        _xScopes.pop();
        _scope.kill();
        for( StructNode init : INITS.values() )
            init.unkeep().kill();
        INITS.clear();
        stop.peephole();
    }

    /**
     *  Parses a function body, assuming the header is parsed.
     */
    private ReturnNode parseFunctionBody( TypeFunPtr sig, Lexer loc, String... ids) {
        // If this is a method, record & restore the existing struct fields,
        // which are also the struct initializers.  The method might update
        // mutable fields, so when the function parse is done reset back to
        // pre-function-parse values.
        Node[] preParse = new Node[_scope.nIns()];
        for( int i=0; i<_scope.nIns(); i++ )
            preParse[i] = _scope.in(i)==null ? null : _scope.in(i).keep();

        // Stack parser state on the local Java stack, and unstack it later
        FunNode oldfun  = _fun;
        ScopeNode breakScope = _breakScope; _breakScope = null;
        ScopeNode continueScope = _continueScope; _continueScope = null;

        FunNode fun = _fun = (FunNode)peep(new FunNode(loc(),sig,_nestedType,null,_code._start));
        // Once the function header is available, install in linker table -
        // allowing recursive functions.  Linker matches on declared args and
        // exact fidx, and ignores the return (because the fidx will only match
        // the exact single function).
        _code.link(fun);

        Node rpc = new ParmNode("$rpc",0,TypeRPC.BOT,fun,con(TypeRPC.BOT)).peephole();

        // Build a multi-exit return point for all function returns
        RegionNode r = new RegionNode( null,null,null).init();
        assert r.inProgress();
        PhiNode rmem = new PhiNode(ScopeNode.MEM0,TypeMem.BOT,r,null).init();
        PhiNode rrez = new PhiNode(ScopeNode.ARG0,Type.BOTTOM,r,null).init();
        ReturnNode ret = new ReturnNode(r, rmem, rrez, rpc, fun).init();
        fun.setRet(ret);
        assert ret.inProgress();
        _code._stop.addDef(ret);


        // Pre-call the function from Start, with worse-case arguments.  This
        // represents all the future, yet-to-be-parsed functions calls and
        // external calls.
        _scope.push(new Kind.Func());
        ctrl(fun);              // Scope control from function
        // Private mem alias tracking per function
        MemMergeNode mem = new MemMergeNode(true);
        mem.addDef(null);       // Alias#0
        mem.addDef(new ParmNode(ScopeNode.MEM0,1,TypeMem.BOT,fun,con(TypeMem.BOT)).peephole()); // All aliases
        _scope.mem(mem);
        // All args, "as-if" called externally
        for( int i=0; i<ids.length; i++ ) {
            Type t = sig.arg(i);
            _scope.define(ids[i], t, false, new ParmNode(ids[i],i+2,t,fun,con(t)).peephole(), loc);
        }

        // Parse the body
        Node last=ZERO;
        while (!peek('}') && !_lexer.isEOF())
            last = parseStatement();

        // Last expression is the return except for the top-level main
        if( ctrl()._type==Type.CONTROL )
            if( fun.sig() != _code._main )
                fun.addReturn(ctrl(), _scope.mem().merge(), last);
            else {
                fun.setDef(1,XCTRL); // Kill default main
                _code.addAll(fun._outputs);
            }

        // Pop off the inProgress node on the multi-exit Region merge
        assert r.inProgress();
        r   ._inputs.pop();
        rmem._inputs.pop();
        rrez._inputs.pop();
        r._loc = loc();         // Final position
        assert !r.inProgress();

        // Force peeps, which have been avoided due to inProgress
        ret.setDef(1,rmem.peephole());
        ret.setDef(2,rrez.peephole());
        ret.setDef(0,r.peephole());
        ret = (ReturnNode)ret.peephole();

        // Function scope ends
        _scope.pop();
        _fun = oldfun;
        _breakScope = breakScope;
        _continueScope = continueScope;

        // Reset all fields to pre-parse days
        for( int i=0; i<preParse.length; i++ )
            _scope.setDef(i,preParse[i]==null ? null : preParse[i].unkeep());

        return ret;
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
    private Node parseBlock(Kind kind) {
        // Enter a new scope
        _scope.push(kind);
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
        if(      matchx("return")  ) return parseReturn();
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
            return require(parseBlock(new Kind.Block()),"}");
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
        _scope.push(new Kind.Block()); // Scope for the index variables
        if( !match(";") )        // Can be empty init "for(;test;next) body"
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

        ctrl(new LoopNode(loc(),ctrl()).peephole()); // Note we set back edge to null here

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
        parseStatement().isKill();                 // Parse loop body
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
        // During sys parsing, there is no xscope here.
        if( !_xScopes.isEmpty() ) {
            _xScopes.pop();
            _xScopes.push( exit );
        }
        _scope = exit;
        return ZERO;
    }

    private ScopeNode jumpTo(ScopeNode toScope) {
        ScopeNode cur = _scope.dup();
        ctrl(XCTRL); // Kill current scope
        // Prune nested lexical scopes that have depth > than the loop head
        // We use _breakScope as a proxy for the loop head scope to obtain the depth
        while( cur.depth() > _breakScope.depth() )
            cur.pop();
        // If this is a continue then first time the target is null
        // So we just use the pruned current scope as the base for the
        // "continue"
        if( toScope == null )
            return cur;
        // toScope is either the break scope, or a scope that was created here
        assert toScope.depth() <= _breakScope.depth();
        toScope.ctrl(toScope.mergeScopes(cur,loc()).peephole());
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
        return parseTrinary(pred,"else");
    }

    // Parse a conditional expression, merging results.
    private Node parseTrinary( Node pred, String fside ) {
        // IfNode takes current control and predicate
        pred.keep();
        Node ifNode = new IfNode(ctrl(), pred).peephole();
        // Setup projection nodes
        Node ifT = new CProjNode(ifNode.  keep(), 0, "True" ).peephole().keep();
        Node ifF = new CProjNode(ifNode.unkeep(), 1, "False").peephole().keep();
        // In if true branch, the ifT proj node becomes the ctrl
        // But first clone the scope and set it as current
        ScopeNode fScope = _scope.dup(); // Duplicate current scope
        _xScopes.push(fScope); // For graph visualization we need all scopes

        // Parse the true side
        ctrl(ifT.unkeep());     // set ctrl token to ifTrue projection
        _scope.addGuards(ifT,pred,false); // Up-cast predicate
        // Parse true-side flavor
        Node lhs = switch( fside ) {
        case "else" -> parseStatement(); // if( pred )  stmts;
        case ":"    -> parseAsgn();      //     pred ?  asgn;
        case "&&"   -> parseLogical();   //     pred && expr
        case "||"   -> pred;             //     pred || expr_is_ignored
        default     -> throw Utils.TODO();
        };
        lhs.keep();
        _scope.removeGuards(ifT);

        // See if a one-sided def was made: "if(pred) int x = 1;" and throw.
        // See if any forward-refs were made, and copy them to the other side:
        // "pred ? n*fact(n-1) : 1"
        fScope.balanceIf(_scope);

        ScopeNode tScope = _scope;

        // Parse the false side
        _scope = fScope;        // Restore scope, then parse else block if any
        ctrl(ifF.unkeep());     // Ctrl token is now set to ifFalse projection
        // Up-cast predicate, even if not else clause, because predicate can
        // remain true if the true clause exits: `if( !ptr ) return 0; return ptr.fld;`
        _scope.addGuards(ifF,pred,true);
        // Parse false-side flavor
        boolean doRHS = false;  // RHS is optional for if/else and trinary
        Node rhs = switch( fside ) {
        case "else" -> (doRHS=match(fside)) ? parseStatement() : con(lhs._type.makeZero());
        case ":"    -> (doRHS=match(fside)) ? parseAsgn()      : con(lhs._type.makeZero());
        case "&&"   -> rhs = pred;
        case "||"   -> rhs = parseLogical();
        default     -> throw Utils.TODO();
        };
        rhs.keep();
        _scope.removeGuards(ifF);
        if( doRHS )
            fScope = _scope;
        pred.unkeep();

        // Check the trinary widening int/flt
        if( !fside.equals("else") ) {
            rhs = widenInt( rhs.unkeep(), lhs._type ).keep();
            lhs = widenInt( lhs.unkeep(), rhs._type ).keep();
        }

        _scope = tScope;
        _xScopes.pop();       // Discard pushed from graph display
        // See if a one-sided def was made: "if(pred) int x = 1;" and throw.
        // See if any forward-refs were made, and copy them to the other side:
        // "pred ? n*fact(n-1) : 1"
        tScope.balanceIf(fScope);

        // Merge results
        RegionNode r = ctrl(tScope.mergeScopes(fScope,loc()));
        Node ret = peep(new PhiNode("",lhs._type.meet(rhs._type).glb(false),r,lhs.unkeep(),rhs.unkeep()));
        // Immediately fail e.g. `arg ? 7 : ptr`
        ParseException err;
        if( !fside.equals("else") && (err=ret.err()) !=null )  throw err;
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
        _fun.addReturn(ctrl(), _scope.mem().merge(), expr);
        ctrl(XCTRL);            // Kill control
        return expr;
    }

    /**
     * Dumps out the node graph
     * @return {@code null}
     */
    Node showGraph() {
        System.out.println(new GraphVisualizer().generateDotOutput(_code._stop,_scope,_xScopes));
        return null;
    }

    /** Parse: [name '='] expr
     */
    private Node parseAsgn() {
        int old = pos();
        String name = _lexer.matchId();
        int old_pre_equals = pos();
        // Just a plain expression, no assignment.
        // Distinguish `var==expr` from `var=expr`
        if( name==null || KEYWORDS.contains(name) || !matchOpx('=','=') )
            {  pos(old);  return parseExpression();  }

        // Find variable to update
        Var def = _scope.lookup(name);
        if( def==null )
            throw error("Undefined name '" + name + "'");

        // If a method var, allow nested blocks and methods, but not fcns or other structs.
        // Else safety check for accessing fields out of function scope.
        if( instance(def) ) {
            pos(old_pre_equals);  // setup for `self.name = expr`
            Node self = _scope.in(_scope.lookup("self")); // Insert self reference
            return parsePostfixName(self,name);
        }

        // TOP fields are for late-initialized fields; these have never
        // been written to, and this must be the final write.  Other writes
        // outside the constructor need to check the final bit.
        if( _scope.in(def._idx)._type!=Type.TOP && def._final &&
            // Inside an allocation, final assign is OK, outside nope.
            // The alloc() call added the allocation scope
            !(_scope.inAllocation() && def._idx >= _scope._kinds.last(-1)._lexSize) )
            throw error("Cannot reassign final '"+name+"'");

        // Parse assignment expression
        Node expr = parseAsgn();

        // Lift expression, based on type
        Node lift = liftExpr(expr.keep(), def.type(), def._final, true);
        // Update
        _scope.update(name,lift);
        // Return un-lifted expr
        return expr.unkeep();
    }

    // Make finals deep; widen ints to floats; narrow wide int types.
    // Early error if types do not match variable.
    private Node liftExpr( Node expr, Type t, boolean xfinal, boolean isLoad ) {
        if( expr._type instanceof TypeMemPtr tmp && TYPES.get(tmp._obj._name).isFRef() )
            throw error("Must define forward ref "+tmp._obj._name);
        // Final is deep on ptrs
        if( xfinal && t instanceof TypeMemPtr tmp ) {
            t = tmp.makeRO();
            expr = peep(new ReadOnlyNode(expr));
        }
        // Auto-widen array to i64 (cast ptr to raw int bits)
        if( t == TypeInteger.BOT && expr._type instanceof TypeMemPtr tmp && tmp._obj.isAry() )
            expr = peep(new AddNode(peep(new CastNode(t,ctrl(),expr)),off(tmp._obj,"[]")));
        // Auto-widen int to float
        expr = widenInt( expr, t );
        // Auto-narrow wide ints to narrow ints.  For loads, emit code to force
        // the loaded value to match the declared sign/zero bits.  For stores,
        // just force the type, acting "as if" the store silently truncates.
        Type et = expr._type;
        if( isLoad ) { expr = zsMask(expr,t); et = expr._type; }
        else if( et instanceof TypeInteger && t instanceof TypeInteger ) et=t;

        // Type is sane
        if( et!=Type.BOTTOM && !et.shallowISA(t) )
            expr = peep(new CastNode(t.isConstant() ? t : t.widen(),null,expr));
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
        int old = pos();
        Type t = type();
        if( peek('.') )         // Ambiguity static vars: "type.var", parse as expression
            { pos(old); t=null; }
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
        Lexer loc = loc();
        String name = requireId();
        // Optional initializing expression follows
        boolean xfinal = false;
        boolean fld_final = false; // Field is final, but not deeply final
        Node expr;
        if( match("=") ) {
            expr = isExternDecl()
                ? externDecl(name,t)
                : parseAsgn();
            // TOP means val and val is always final
            xfinal = (t==Type.TOP) ||
                expr instanceof ExternNode ||
                // BOTTOM is var and var is always not-final
                (t!=Type.BOTTOM &&
                 // no Bang AND
                 !hasBang &&
                 // Locals are not-final by default
                 !_scope.inFunction() &&
                 // not-null (expecting null to be set to not-null)
                 expr._type != Type.NIL &&
                 // Pointers are final by default; int/flt are not-final by default.
                 (t instanceof TypeNil));

            // var/val, then type comes from expression
            if( inferType ) {
                if( expr._type==Type.NIL )
                    throw error("a not-null/non-zero expression");
                t = expr._type;
                if( !xfinal ) t = t.glb(false);  // Widen if not final
            }
            // expr is a constant function
            if( t instanceof TypeFunPtr && expr._type instanceof TypeFunPtr tfp && tfp.isConstant() ) {
                if( expr instanceof ExternNode ) t = expr._type; // Upgrade declared type to exact function
                else _code.link(tfp).setName(name); // Assign debug name to Simple function
            }

        } else {
            // Need an expression to infer the type.
            // Also, if not-null then need an initializing expression.
            // Allowed in a class def, because type/init will happen in the constructor.
            if( (inferType || (t instanceof TypeNil tn && !tn.nullable() )) && !_scope.inConstructor() )
                throw errorSyntax("=expression");
            // Initial value for uninitialized struct fields.
            expr = switch( t ) {
                // Nullable pointers get a NIL; not-null get a TOP which
                // signals that they *must* be initialized in the constructor.
            case TypeNil tn -> tn.nullable() ? NIL : con(Type.TOP);
            case TypeInteger ti -> ZERO;
            case TypeFloat tf -> con(TypeFloat.FZERO);
            // Bottom signals type inference: they must be initialized in
            // the constructor and that's when we'll discover the type.
            case Type tt -> { assert tt==Type.BOTTOM; yield con(tt); }
            };
            // Nullable fields are set in the constructor, but remain shallow final.
            // e.g. final pointer to a r/w array
            if( t instanceof TypeNil tn && !tn.nullable() && !hasBang )
                fld_final = true;
        }

        // Lift expression, based on type
        Node lift = liftExpr(expr, t, xfinal, true);
        // Rebuild the deep RO 't', not returned from liftExpr
        if( xfinal && t instanceof TypeMemPtr tmp )
            t = tmp.makeRO();

        // Lift type to the declaration.  This will report as an error later if
        // we cannot lift the type.
        if( !lift._type.isa(t) )
            lift = peep(new CastNode(t.widen(),null,lift));
        // Define a new name
        if( !_scope.define(name,t,xfinal || fld_final,lift, loc) )
            throw error("Redefining name '" + name + "'", loc);
        return lift;
    }



    /**
     * Parse a struct declaration, and return the following statement.
     * Structs cannot be redefined, but can be nested.
     *
     * @return zero
     */
    private String _nestedType;
    private Node parseStruct() {
        // "struct" already parsed, so expect the struct name next.
        String old = _nestedType;
        String typeName = requireId();
        if( old!=null ) typeName = old+"."+typeName;
        _nestedType = typeName;
        // New declared or forward-ref type
        TypeStruct ts = TYPES.get(typeName) instanceof TypeMemPtr tmp ? tmp._obj : TypeStruct.open(typeName);
        TYPES.put(typeName,TypeMemPtr.make(ts));

        // A Block scope parse, and inspect the scope afterward for fields.
        _scope.push(new Kind.Define(TypeMemPtr.make(ts)));
        require("{");
        int lexlen = _scope._kinds.last()._lexSize, nvar=lexlen;
        while (!peek('}') && !_lexer.isEOF()) {
            parseStatement().isKill();
            while( nvar < _scope._vars._len ) {
                Var v = _scope.var(nvar++);
                if( !v.isFRef() ) {
                    ts = ts.add( Field.make( v._name, v.type(), _code.getALIAS(), v._final, v._final && _scope.in( v._idx )._type != Type.TOP ) );
                    TYPES.put( typeName, TypeMemPtr.make( ts ) );
                }
            }
        }

        // Count struct declarations
        int varlen = _scope._vars._len;
        ts = ts.close();
        TYPES.put(typeName,TypeMemPtr.make(ts));

        // Generate an initializing struct
        StructNode s = new StructNode(ts);
        for( int i=lexlen; i<varlen; i++ )
            if( !_scope.var(i).isFRef() ) // Promote to outer scope, not defined here
                s.addDef(_scope.in(i)); // Keep the program code to generate instance

        // Save instance default constructor
        s.setType(s.compute()); // Do not call peephole, in case the entire struct is a big plain constant
        INITS.put(typeName,s.keep());

        // Having finalized the struct, upgrade all the 'self' pointers
        for( Node n : s._inputs )
            if( n._type instanceof TypeFunPtr tfp && _code.link(tfp) instanceof FunNode fun )
                _code.addAll( fun._outputs );


        // Done with struct/block scope
        _nestedType = old;
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

        // Check for subtype.
        while( true ) {
            int old2 = pos();
            if( !match(".") )  break;
            String sname = _lexer.matchId();
            if( sname==null ) { pos(old2); break; }
            String tsname = tname+"."+sname;
            t0 = TYPES.get(tsname);
            if( t0==null ) { pos(old2); t0 = TYPES.get(tname); break; }
            tname = tsname;
        }

        // Still no type found?  Assume forward reference
        Type t1 = t0 == null ? TypeMemPtr.make(TypeStruct.open(tname)) :t0; // Null: assume a forward ref type
        // Nest arrays and '?' as needed
        Type t2 = t1;
        while( true ) {
            if( match("?") ) {
                if( !(t2 instanceof TypeMemPtr tmp) )
                    throw error("Type "+t0+" cannot be null");
                if( tmp.nullable() ) throw error("Type "+t2+" already allows null");
                t2 = tmp.makeNullable();
            } else if( match("[~]") ) {
                t2 = typeAry(t2,true);
            } else if( match("[]") ) {
                t2 = typeAry(t2,false);
            } else
                break;
        }

        // Check no forward ref
        if( t0 != null ) return t2;
        // Check valid forward ref, after parsing all the type extra bits.
        // Cannot check earlier, because cannot find required 'id' until after "[]?" syntax
        int old2 = pos();
        match("!");
        String id = _lexer.matchId();
        if( !(peek(',') || peek(';') || match("->")) || id==null )
            return posT(old1);  // Reset lexer to reparse
        pos(old2);              // Reset lexer to reparse
        // Yes a forward ref, so declare it
        TYPES.put(tname,t1);
        // Return the (array, final) type
        return t2;
    }

    // Make an array type of t
    private TypeMemPtr typeAry( Type t, boolean efinal ) {
        if( t instanceof TypeMemPtr tmp && tmp.notNull()  )
            throw error("Arrays of reference types must always be nullable");
        String tname = "[]"+t.str();
        TypeMemPtr ta = (TypeMemPtr)TYPES.get(tname);
        if( ta==null )
            // Remember final version
            TYPES.put(tname, ta = TypeMemPtr.make( TypeStruct.makeAry(tname,TypeInteger.U32,_code.getALIAS(),t,_code.getALIAS(),true) ));
        Field elem = ta._obj.field("[]");
        if( elem._final == efinal )
            return ta;
        return TypeMemPtr.make( ta._obj.replace(elem.makeFrom(efinal)));
    }

    // A function type is `{ type... -> type }` or `{ type }`.
    private Type typeFunPtr() {
        int old = pos();        // Record lexer position
        match("{");             // Skip already-peeked '{'
        Type t0 = type();       // Either return or first arg
        if( t0==null ) return posT(old); // Not a function
        if( match("}") )                 // No-arg function { -> type }
            return TypeFunPtr.make(match("?"),false,TypeFunPtr.TEMPTY,t0);
        Ary<Type> ts = new Ary<>(Type.class);
        ts.push(t0);            // First argument
        while( true ) {
            if( match("->") ) { // End of arguments, parse return
                Type ret = type();
                if( ret==null || !match("}") )
                    return posT(old); // Not a function
                return TypeFunPtr.make(match("?"),false,ts.asAry(),ret);
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
        Node expr = parseLogical();
        return match("?") ? parseTrinary(expr,":") : expr;
    }

    /**
     * Parse an bitwise expression
     *
     * <pre>
     *     bitwise : compareExpr (('&' | '|' | '^') compareExpr)*
     * </pre>
     * @return a bitwise expression {@link Node}, never {@code null}
     */

    private Node parseLogical() {
        Node lhs = parseBitwise();
        while (true) {
            if( false ) ;
            else if( match("&&") ) lhs = parseTrinary(lhs, "&&");
            else if( match("||") ) lhs = parseTrinary(lhs, "||");
            else break;
        }
        return lhs;
    }

    private Node parseBitwise() {
        Node lhs = parseEquality();
        while( true ) {
            if( false ) ;
            else if( matchOp('&') ) lhs = new AndNode(loc(),lhs,null);
            else if( matchOp('|') ) lhs = new  OrNode(loc(),lhs,null);
            else if( match  ("^") ) lhs = new XorNode(loc(),lhs,null);
            else break;
            lhs.setDef(2,parseEquality());
            lhs = peep(lhs);
        }
        return lhs;
    }



    /**
     * Parse an eq/ne expression
     */
    private Node parseEquality() {
        Node lhs = parseComparison();
        while( true ) {
            boolean eq = false;
            if( match("==") ) eq = true;
            else if( !match("!=") ) break;
            lhs.keep();
            Node rhs = parseComparison();
            lhs = peep(new BoolNode.EQ(lhs.unkeep(),rhs).widen());
            if( !eq ) lhs = peep(new NotNode(lhs));
        }
        return lhs;
    }

    /**
     * Parse an expression of the form:
     *
     * <pre>
     *     expr : shiftExpr < shiftExpr <= shiftExpr...
     *     expr : shiftExpr > shiftExpr >= shiftExpr...
     * </pre>
     * @return an comparator expression {@link Node}, never {@code null}
     */
    private Node parseComparison() {
        Node lhs = parseShift();
        int dir = 0;

        while( true ) {
            int dir0;
            if( false ) ;
            else if( match("<=" ) ) dir0 = -1;
            else if( match(">=" ) ) dir0 = -2;
            else if( match("<"  ) ) dir0 =  1;
            else if( match(">"  ) ) dir0 =  2;
            else break;
            lhs.keep();
            // Check and record direction
            if( dir==0 ) {
                dir = Math.abs(dir0);
                Node rhs = parseShift();
                lhs = makeCompBool(dir0,lhs.unkeep(),rhs); // Convert to a bool
            } else if( dir == Math.abs(dir0) ) {
                // e0 < lhs < ???
                Node ifNode = new IfNode(ctrl(), lhs).peephole();
                Node ifT = new CProjNode(ifNode.  keep(), 0, "True" ).peephole();
                Node ifF = new CProjNode(ifNode.unkeep(), 1, "False").peephole();
                // False side does nothing
                ScopeNode fScope = _scope.dup();
                fScope.ctrl(ifF);
                // True side executes next part of range
                ctrl(ifT);
                _scope.addGuards(ifT,lhs,false);
                Node rhs = parseShift();
                _scope.removeGuards(ifT); // TODO: I think should remain true as long as possible
                Node next = makeCompBool(dir0, lhs, rhs).keep();
                // Merge result
                RegionNode r = ctrl(_scope.mergeScopes(fScope, loc()));
                assert next._type.meet(lhs._type).isa( TypeInteger.BOOL );
                lhs = peep(new PhiNode("",TypeInteger.BOOL,r,next.unkeep(),lhs.unkeep()));
                r.peephole();
            } else {
                throw error("Mixing relational directions in a chained relational test");
            }
        }
        return lhs;
    }

    private Node makeCompBool( int dir0, Node lhs, Node rhs ) {
        // Convert to a bool
        if( Math.abs(dir0)==2 ) // Swap direction
            { Node tmp = lhs; lhs = rhs; rhs = tmp; }
        lhs = peep(dir0 < 0 ? new BoolNode.LE(lhs,rhs) : new BoolNode.LT(lhs,rhs));
        return peep(lhs.widen());
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
            else if( match("<<") ) lhs = new ShlNode(loc(),lhs,null);
            else if( match(">>>")) lhs = new ShrNode(loc(),lhs,null);
            else if( match(">>") ) lhs = new SarNode(loc(),lhs,null);
            else break;
            lhs.setDef(2,parseAddition());
            ParseException err;
            if( (err=lhs.err()) != null )
                throw err;
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
                Var n = _scope.lookup(name);
                if( n != null && !(n.type() instanceof TypeMemPtr) ) {
                    if( n._final )
                        throw error("Cannot reassign final '"+n._name+"'");
                    Node expr = n.type() instanceof TypeFloat
                        ?        peep(new AddFNode(_scope.in(n),con(TypeFloat.constant(delta))))
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
        // Else be a primary expression
        return parsePrimary();
    }

    /**
     * Parse a primary expression:
     *
     * <pre>
     *     primaryExpr : integerLiteral | "string" | 'char' | true | false | null |
     *                   new Type | '(' expression ')' | Id['++','--'] |
     *                   [static.]* Id
     * </pre>
     * @return a primary {@link Node}, never {@code null}
     */
    private Node parsePrimary() {
        if( _lexer.isNumber(_lexer.peek()) ) return parseLiteral();
        if( _lexer.peek('"') ) return newString(parseString());
        if( matchx("true" ) ) return con(1);
        if( matchx("false") ) return ZERO;
        if( matchx("null" ) ) return NIL;
        if( match ("'"    ) ) return parseChar();
        if( match ("("    ) ) return parsePostfix(require(parseAsgn(), ")"));
        if( matchx("new"  ) ) return parsePostfix(alloc());
        if( match ("{"    ) ) return parsePostfix(require(func(),"}"));

        // Parse static variable lookup: `type.fld` in the type namespace
        // CNC: not a full type parse, just a typename parse
        int pos = pos();
        Type t = type();
        if( t!=null ) {
            if( peek('.') )
                return parsePostfix(con(t));
            //pos(pos); // TODO: not a `type.fld`
            //return null;
            throw Utils.TODO();
        }

        // Expect an identifier now

        // - if null, make fref, normal var handling
        // - if final constant, normal var handling
        // - if method var, scan skipping blocks & methods, must hit correct struct no fcns
        // - if normal var, scan skipping blocks, allowing last fcn
        // - NO fcns nested in methods, NO methods nested in fcns

        String id = _lexer.matchId();
        if( id == null || KEYWORDS.contains(id) )
            throw errorSyntax("an identifier or expression");
        Var var = _scope.lookup(id);

        if( var==null ) {
            // If missing, assume a forward reference
            _scope.define(id, FRefNode.FREF_TYPE, true, XCTRL, loc());
            var = _scope.lookup(id);
        }

        // If a method var, allow nested blocks and methods, but not fcns or other structs.
        // Else safety check for accessing fields out of function scope.
        if( instance(var) ) {
            Node self = _scope.in(_scope.lookup("self")); // Insert self reference
            return parsePostfixName(self,id);
        }

        // Load local value
        Node rvalue = _scope.in(var);
        if( rvalue._type == Type.BOTTOM )
            if( rvalue instanceof FRefNode )
                return parsePostfix(rvalue);
            else throw error("Cannot read uninitialized field '"+id+"'");

        // Check for assign-update, x += e0;
        char ch = _lexer.matchOperAssign();
        if( ch==0  )            // Normal primary, check for postfix updates
            return parsePostfix(rvalue);
        // Assign-update direct into Scope
        Node op = opAssign(ch,rvalue, var.type() );
        _scope.update(var,op);
        return postfix(ch) ? rvalue.unkeep() : op;
    }

    // true if instance variable lookup (var is in any number of nested blocks,
    // then an instance function then inside a struct declaration).
    private boolean instance(Var var ) {
        // If a method var, allow nested blocks and methods, but not fcns or other structs
        Kind kind = _scope.kind(var);
        if( kind instanceof Kind.Define ) {
            for( int i = _scope.depth()-1; i>=0 && var._idx < _scope._kinds.at(i)._lexSize; i-- ) {
                Kind xkind = _scope._kinds.at(i);
                if( xkind instanceof Kind.Block ) continue;
                if( xkind instanceof Kind.Func ) {
                    if( _scope._kinds.at(i-1)==kind )
                        return true; // Instance variable: lookup in a method in a decl
                }
                throw Utils.TODO();
            }
        } else {
            // If a normal var, allow any nested blocks, and a last fcn -
            // but not functions further out (which involves capture).
            Node def = _scope.in(var._idx);
            for( int i = _scope.depth()-1; i>=0 && var._idx < _scope._kinds.at(i)._lexSize; i-- )
                if( _scope._kinds.at(i) instanceof Kind.Func &&
                    !(def instanceof FRefNode) && !(var._final && def._type.isConstant()) )
                    throw error("Variable '"+var._name+"' is out of function scope and must be a final constant");
        }
        return false;
    }

    // Check for assign-update, "x += e0".
    // Returns "x + e0"; the caller assigns value.
    // if ch is postfix, then lhs is kept and caller will unkeep and return it.
    private Node opAssign( char ch, Node lhs, Type t ) {
        // RHS of the update.
        lhs.keep();             // Alive across parseAsgn
        Node rhs =
            (byte)ch ==  1 ? con( 1) : // var++
            (byte)ch == -1 ? con(-1) : // var--
            parseAsgn();               // var op= rhs
        if( !postfix(ch) ) lhs.unkeep();   // Allow to die in next peep
        // 4 cases:
        // int + int ==>> narrow int
        // int + flt ==>> error, caller must fail assigning flt into int
        // flt + int ==>> use float op, wrap toFloat()
        // flt + flt ==>> use float op
        Node op = switch(ch) {
        case 1, (char)-1,
             '+' -> new AddNode(lhs,rhs);
        case '-' -> new SubNode(lhs,rhs);
        case '*' -> new MulNode(lhs,rhs);
        case '/' -> new DivNode(lhs,rhs);
        default  -> throw Utils.TODO();
        };
        // Convert to float ops, or narrow int types; error if not declared type.
        // Also, if postfix LHS is still keep()
        return liftExpr(peep(op.widen()),t,false,true);
    }


    /**
       Parse an allocation
     */
    private Node alloc() {
        Type t = type();
        if( t==null ) throw error("Expected a type");
        // Parse ary[ length_expr ]
        if( match("[") ) {
            if( !t.makeZero().isa(t) )
                throw error("Cannot allocate a non-nullable, since arrays always zero/null fill");
            Node len = parseAsgn();
            if( !(len._type instanceof TypeInteger) )
                throw error("Cannot allocate an array with length "+len._type);
            require("]");
            TypeMemPtr tmp = typeAry(t,false);
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
            _scope.push(new Kind.Block());
            Lexer loc = loc();
            for( int i=0; i<fs.length; i++ )
                // An initial TOP means the field needs to be initialized.  We
                // store a BOT initially; any partial init will fall to BOT
                // (merge BOT and the partial) and be obviously only a partial
                // init.  To be initialized the field needs a full clobber.
                _scope.define(fs[i]._fname, fs[i]._t, fs[i]._final, s.in(i)._type==Type.TOP ? con(Type.BOTTOM) : s.in(i), loc);
            // Parse the constructor body
            require(parseBlock(new Kind.Alloc(tmp)),"}");
            init = _scope._inputs;
        }
        // Check that all fields are initialized
        for( int i=idx; i<init.size(); i++ )
            if( init.at(i)._type == Type.TOP || init.at(i)._type == Type.BOTTOM )
                throw error("'"+tmp._obj._name+"' is not fully initialized, field '" + fs[i-idx]._fname + "' needs to be set in a constructor");
        Node ptr = newStruct(tmp._obj, off(tmp._obj, " len"), idx, init );
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
        Node[] ns = new Node[2+len];
        ns[0] = ctrl();         // Control in slot 0
        // Total allocated length in bytes
        ns[1] = size;
        // Memory aliases for every field
        for( int i = 0; i < len; i++ )
            if( !fs[i]._one )
                ns[2+i] = memAlias(fs[i]._alias);
        Node nnn = new NewNode(TypeMemPtr.make(obj), ns).peephole().keep();
        for( int i = 0; i < len; i++ )
            if( !fs[i]._one )
                memAlias(fs[i]._alias, new ProjNode(nnn,i+2,memName(fs[i]._alias)).peephole());
        Node ptr = new ProjNode(nnn.unkeep(),1,obj._name).peephole().keep();

        // Initial nonzero values for every field
        for( int i = 0; i < len; i++ ) {
            Node val = init.get( i + idx );
            if( !fs[i]._one && val._type != val._type.makeZero() ) {
                Node mem = memAlias(fs[i]._alias);
                Node st = new StoreNode(loc(),fs[i]._fname,fs[i]._alias,fs[i]._t,mem,ptr,off(obj,fs[i]._fname),val,true).peephole();
                memAlias(fs[i]._alias,st);
            }
        }

        return ptr.unkeep();
    }

    private static final Ary<Node> ALTMP = new Ary<>(Node.class);
    private Node newArray(TypeStruct ary, Node len) {
        ConFldOffNode base = off(ary, "[]");
        int scale= ary.aryScale();
        Node size = peep(new AddNode(base,peep(new ShlNode(null,len.keep(),con(scale)))));
        ALTMP.clear();  ALTMP.add(len.unkeep()); ALTMP.add(con(ary._fields[1]._t.makeZero()));
        return newStruct(ary,size,0,ALTMP);
    }
    private Node newString(String s) {
        TypeMemPtr tmp = typeAry(TypeInteger.U8,true);
        int  lenAlias = tmp._obj.field("#" )._alias;
        int elemAlias = tmp._obj.field("[]")._alias;
        TypeConAryB con = TypeConAryB.make(s);
        Type elem = con.elem();
        // Make a TMP, not-null (byte)2, singleton (true) (requires text
        // strings are hash-interned), with a TypeStruct having a constant
        // array body.
        TypeMemPtr str = TypeMemPtr.make((byte)2,TypeStruct.makeAry("[]u8", TypeInteger.constant(s.length()), lenAlias, con, elemAlias, true),true);
        assert str.isConstant();
        return con(str);
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
     *     expr ('.' FIELD)* [ = expr ]       // Field reference
     *     expr '#'                           // Postfix unary read operator
     *     expr ['++' | '--' ]                // Postfix unary write operator
     *     expr ('[' expr ']')* = [ = expr ]  // Array reference
     *     expr '(' [args,]* ')'              // Function call
     * </pre>
     */
    private Node parsePostfix(Node expr) {
        String name;
        if( match(".") )      name = requireId();
        else if( match("#") ) name = "#";
        else if( match("[") ) name = "[]";
        else if( match("(") ) return parsePostfix(require(functionCall(expr,null),")"));
        else return expr;       // No postfix

        return parsePostfixName(expr,name);
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
    private Node parsePostfixName(Node expr, String name) {

        if( expr._type==Type.NIL )
            throw error("Accessing unknown field '" + name + "' from 'null'");

        // Sanity check expr for being a reference
        if( !(expr._type instanceof TypeMemPtr ptr) )
            throw error( "Expected "+(name=="#" || name=="[]" ? "array" : "reference")+" but found " + expr._type.str() );

        // Find the field from the Type.  Lookup in the base object field names.
        TypeMemPtr ptr2 = (TypeMemPtr)TYPES.get(ptr._obj._name);
        ptr = (TypeMemPtr)ptr.join(ptr2);// Upgrade to latest TYPES
        TypeStruct base = ptr._obj;
        int fidx = base.find(name);
        if( fidx == -1 )
            throw error("Accessing unknown field '" + name + "' from '" + ptr.str() + "'");

        // Get field type and layout offset from base type and field index fidx
        Field f = base._fields[fidx];  // Field from field index
        Type tf = f._t;
        if( tf instanceof TypeMemPtr ftmp && ftmp.isFRef() )
            tf = ftmp.makeFrom(((TypeMemPtr)(TYPES.get(ftmp._obj._name)))._obj);
        if( base.isAry() && tf instanceof TypeConAry con )
            tf = con.elem();

        // Field offset; fixed for structs, computed for arrays
        Node off = (name.equals("[]")       // If field is an array body
            // Array index math
            ? peep(new AddNode(off(base,"[]"),peep(new ShlNode(null,require(parseAsgn(),"]"),con(base.aryScale())))))
            // Struct field offsets are hardwired
            : off(base,name)).keep();

        // Disambiguate "obj.fld==x" boolean test from "obj.fld=x" field assignment
        if( matchOpx('=','=') ) {
            Node val = parseAsgn().keep();
            Node lift = liftExpr( val, tf, f._final, false );

            Node st = new StoreNode(loc(), name, f._alias, tf, memAlias(f._alias), expr, off.unkeep(), lift, false);
            // Arrays include control, as a proxy for a safety range check.
            // Structs don't need this; they only need a NPE check which is
            // done via the type system.
            if( base.isAry() )  st.setDef(0,ctrl());
            memAlias(f._alias, st.peephole());
            return val.unkeep();        // "obj.a = expr" returns the expression while updating memory
        }

        // Keep expr for possible store update
        expr.keep();
        // Load field
        Node load = f._one && INITS.containsKey( base._name )
            ? INITS.get(base._name).in(base.find(name))
            : new LoadNode(loc(),name, f._alias, tf, memAlias(f._alias), expr, off);
        // Arrays include control, as a proxy for a safety range check
        // Structs don't need this; they only need a NPE check which is
        // done via the type system.
        if( base.isAry() && !name.equals("#") ) load.setDef(0,ctrl());
        load = peep(load);

        // Check for assign-update, "ptr.fld += expr" or "ary[idx]++"
        char ch = _lexer.matchOperAssign();
        if( ch!=0 ) {
            Node op = opAssign(ch,load, tf );
            Node st = new StoreNode(loc(), name, f._alias, tf, memAlias(f._alias), expr.unkeep(), off.unkeep(), op, false);
            // Arrays include control, as a proxy for a safety range check.
            // Structs don't need this; they only need a NPE check which is
            // done via the type system.
            if( base.isAry() )  st.setDef(0,ctrl());
            memAlias(f._alias, peep(st));
            load = postfix(ch) ? load.unkeep() : op;
            // And use the original loaded value as the result
            return load;
            //throw Utils.TODO(); // NO POSTFIX AFTER STORE, JUST RETURN
        } else {
            off.unkill();
            // Method call: Loaded a function from a named field, AND the base
            // field type is final.
            if( load._type instanceof TypeFunPtr && name!="[]" &&
                !(load instanceof ExternNode) &&
                ((TypeMemPtr)TYPES.get(base._name))._obj.field(name)._final &&
                // And calling a function
                match("(") ) {
                return parsePostfix(require(functionCall(load,expr),")"));
            }

            // Drop expr, keeping load alive
            load.keep();
            expr.unkill();
            load.unkeep();
        }

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
            return peep(new AndNode(null,val,con(t0._max)));
        // Signed extension
        int shift = Long.numberOfLeadingZeros(t0._max)-1;
        Node shf = con(shift);
        if( shf._type==TypeInteger.ZERO )
            return val;
        return peep(new SarNode(null,peep(new ShlNode(null,val,shf.keep())),shf.unkeep()));
    }

    /**
     * Parse a function body; the caller will parse the surrounding "{}"
     *
     * <pre>
     *     { [type arg,]* -> expr }
     *     { expr } // The no-argument function
     * </pre>
     */
    private Node func() {
        Ary<Type> ts = new Ary<>(Type.class);
        Ary<String> ids = new Ary<>(String.class);

        // Defined in constructor?  Add `self` argument.  "static" calls still
        // add a `self` they just ignore it.
        if( _scope._kinds.last() instanceof Kind.Define define ) {
            // Upgrade defined struct to latest fields
            TypeMemPtr tmp = (TypeMemPtr)TYPES.get(define._tmp._obj._name);
            ts .push(tmp);
            ids.push("self");
        }

        // Parse other arguments
        _lexer.skipWhiteSpace();
        Lexer loc = loc();      // First argument location
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
        TypeFunPtr tfp = _code.makeFun(TypeFunPtr.make(false,false,ts.asAry(),Type.BOTTOM));
        ReturnNode ret = parseFunctionBody(tfp,loc,ids.asAry());
        return con(ret._fun.sig());
    }

    /**
     *  Parse function call arguments; caller will parse the surrounding "()"
     * <pre>
     *   ( arg* )
     * </pre>
     */
    private Node functionCall(Node fcn, Node self) {
        if( fcn._type == Type.NIL )
            throw error("Calling a null function pointer");
        if( !(fcn instanceof FRefNode) && !fcn._type.isa(TypeFunPtr.BOT) )
            throw error("Expected a function but got "+fcn._type.glb(false).str());
        fcn.keep();            // Keep while parsing args

        Ary<Node> args = new Ary<>( Node.class );
        args.push(null);        // Space for ctrl,mem
        args.push(null);
        if( self != null )      // Method call has a self
            args.push(self);
        while( !peek(')') ) {
            Node arg = parseAsgn();
            if( arg==null ) break;
            args.push(arg.keep());
            if( !match(",") ) break;
        }
        // Control & memory after parsing args
        args.set(0,ctrl().keep());
        args.set(1,_scope.mem().merge().keep());
        args.push(fcn);        // Function pointer
        // Unkeep them all
        for( Node arg : args )
            arg.unkeep();
        // Dead into the call?  Skip all the node gen
        if( ctrl()._type == Type.XCONTROL ) {
            for( Node arg : args )
                if( arg.isUnused() )
                    arg.kill();
            return con(Type.TOP);
        }

        // Into the call
        CallNode call = (CallNode)new CallNode(loc(), args.asAry()).peephole();
        // Post-call setup
        CallEndNode cend = (CallEndNode)new CallEndNode(call).peephole();
        call.peephole();        // Rerun peeps after CallEnd, allows early inlining
        // Control from CallEnd
        ctrl(new CProjNode(cend,0,ScopeNode.CTRL).peephole());
        // Memory from CallEnd
        MemMergeNode mem = new MemMergeNode(true);
        mem.addDef(null);       // Alias#0
        mem.addDef(new ProjNode(cend,1,ScopeNode.MEM0).peephole());
        _scope.mem(mem);
        // Call result
        return new ProjNode(cend,2,"#2").peephole();
    }

    // Just after parsing "type foo = " can parse `"C"`
    private boolean isExternDecl( ) {
        int old = pos();
        String s = parseString();
        if( "C".equals(s) ) return true;
        pos(old);
        return false;
    }
    // External linked constant
    private Node externDecl( String ex, Type t ) {
        if( t instanceof TypeFunPtr tfp ) // Generic TFP from type parse
            t  = _code.makeFun(tfp);      // Get a FIDX, becomes a constant
        return new ExternNode(t,ex).peephole();
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
    public static ConFldOffNode off( TypeStruct base, String fname ) { return (ConFldOffNode)(new ConFldOffNode(base._name,fname).peephole()); }
    public Node peep( Node n ) {
        // Peephole, then improve with lexically scoped guards
        return _scope.upcastGuard(n.peephole());
    }

    // Parse a string or null
    private String parseString() {
        if( !peek('"') ) return null;
        _lexer.inc();
        int start = pos();
        while( !_lexer.isEOF() && _lexer.nextChar()!= '"' ) ;
        if( _lexer.isEOF() )
            throw error("Unclosed string");
        return new String(_lexer._input,start,pos()-start-1);
    }

    // Already parsed "'"
    private Node parseChar() {
        return require(con(TypeInteger.constant(_lexer.nextChar())),"'");
    }

    //////////////////////////////////
    // Utilities for lexical analysis

    // Return true and skip if "syntax" is next in the stream.
    private boolean match (String syntax) { return _lexer.match (syntax); }
    // Match must be "exact", not be followed by more id letters
    private boolean matchx(String syntax) { return _lexer.matchx(syntax); }
    private boolean matchOp(char c0 ) { return _lexer.matchOp(c0);  }
    private boolean matchOpx(char c0, char c1) { return _lexer.matchOpx(c0,c1);  }
    // Return true and do NOT skip if 'ch' is next
    private boolean peek(char ch) { return _lexer.peek(ch); }
    private boolean peekIsId() { return _lexer.peekIsId(); }

    // ch is +/- 1, means oper++ or oper-- means postfix
    private static boolean postfix(char ch) {
        return (byte)ch== 1 || (byte)ch== -1;
    }

    public int pos() { return _lexer._position; }
    private int pos(int pos) {
        int old = _lexer._position;
        _lexer._position = pos;
        return old;
    }
    private Type posT(int pos) { _lexer._position = pos; return null; }
    // Source code location
    Lexer loc() { return new Lexer(_lexer); }


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

    ////////////////////////////////////
    // Lexer components

    // Lexer provides low level access to the raw file bytes, peeks and matches
    // short strings, parses numbers, skips comments and whitespace, tracks
    // line numbers, allows the parse position to be saved and restored, and
    // serves as a location indicator for errors.

    public static class Lexer {

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

        /**
         *  Copy a lexer from a lexer
         */
        private Lexer(Lexer l) {
            _input = l._input;
            _position = l._position;
            _line_number = l._line_number;
            _line_start = l._line_start;
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
                    while( !isEOF() && _input[_position] != '\n' )
                        inc();
                } else if( _position+2 < _input.length &&
                         _input[_position  ] == '/' &&
                         _input[_position+1] == '*') {
                    // Skip /*comment*/
                    while( !isEOF() && !(_input[_position-1] == '*' && _input[_position] == '/'))
                        inc();
                    inc();
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
        // Match this char, and the next char must be different.
        // Handles '&&' vs '&'
        boolean matchOp( char c0 ) {
            skipWhiteSpace();
            if( _position+1 >= _input.length || _input[_position]!=c0 || _input[_position+1]==c0 )
                return false;
            inc();
            return true;
        }
        // Match these two characters in a row
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
                    throw error("Syntax error: integer values cannot start with '0'",this);
                long i = Long.parseLong(new String(_input,old,len));
                return TypeInteger.constant(i);
            }
            return TypeFloat.constant(Double.parseDouble(new String(_input,old,-len)));
        }
        private String parseNumberString() {
            int old = _position;
            int len = Math.abs(isLongOrDouble());
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
            if( peek() == '-' ) nextChar();
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
    }

    ParseException errorSyntax(String syntax) { return _errorSyntax("expected "+syntax);  }
    private ParseException _errorSyntax(String msg) {
        return error("Syntax error, "+msg+": " + _lexer.getAnyNextToken());
    }
    ParseException error(String msg) { return error(msg,_lexer); }
    public static ParseException error(String msg, Lexer loc) { return new ParseException(msg,loc); }

    public static class ParseException extends RuntimeException {
        public final Lexer _loc;
        // file:line:charoff err
        //String msg = "src:"+_line_number+":"+(_position-_line_start)+" "+errorMessage;
        ParseException( String msg, Lexer loc ) { super(msg);  _loc = loc; }
    }

}
