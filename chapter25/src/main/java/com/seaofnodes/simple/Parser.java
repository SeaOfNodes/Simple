package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.print.GraphVisualizer;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.Utils;
import com.seaofnodes.simple.node.ScopeNode.Kind;
import static com.seaofnodes.simple.util.Utils.TODO;

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


    // Class prefix string; TODO: use something short: "%"
    private static final String clzPrefix = "class:";
    public static String addClzPrefix( String x ) {
        assert !x.startsWith(clzPrefix);
        return (clzPrefix+x).intern();
    }
    public static boolean startsClzPrefix( String s ) {
        return s.startsWith(clzPrefix);
    }


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

    ScopeNode _breakScope;      // Merge all the while-breaks    here
    ScopeNode _continueScope;   // Merge all the while-continues here
    ScopeNode _returnScope;     // Merge all the function exits  here

    // Mapping from a type name to a Type.  The string name matches
    // `type.str()` call.
    public static HashMap<String, Type> TYPES;


    public Parser(CodeGen code ) {
        _code = code;
        _scope = new ScopeNode();
        _continueScope = _breakScope = null;
        ZERO  = con(TypeInteger.ZERO).keep();
        NIL  = con(Type.NIL).keep();
        XCTRL= new XCtrlNode().peephole().keep();
        TYPES = defaultTypes();
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

    // Read and set CTRL
    private Node ctrl() { return _scope.ctrl(); }
    private <N extends Node> N ctrl(N n) { return _scope.ctrl(n); }
    // Read and set Memory
    private MemMergeNode mem() { return _scope.mem(); }
    private void mem(Node mem) { _scope.mem(mem); }
    // We set up memory aliases by inserting special vars in the scope these
    // variables are prefixed by $ so they cannot be referenced in Simple code.
    // Using vars has the benefit that all the existing machinery of scoping
    // and phis work as expected
    private Node memAlias(int alias         ) { return _scope.mem(alias    ); }
    private void memAlias(int alias, Node st) {        _scope.mem(alias, st); }
    public static String memName(int alias) { return ("$"+alias).intern(); }


    public void parse() {
        _lexer = new Lexer(_code._src);
        // Starting Scope has control, memory, initial arguments
        _scope.define(ScopeNode.CTRL, Type.CONTROL   , false, null, _lexer);
        _scope.define(ScopeNode.MEM0, TypeMem.BOT    , false, null, _lexer);
        //_scope.define(ScopeNode.ARG0, TypeInteger.BOT, false, null, _lexer);
        // Track active scopes for Graph display
        _xScopes.push(_scope);

        //
        ctrl(XCTRL);
        mem(new MemMergeNode(false));

        // File-level struct declaration
        _nestedType = _code._srcName;
        String typeName = addClzPrefix(_code._srcName);

        ReturnNode clzret = parseStruct( true, typeName );

        if( !_lexer.isEOF() ) throw _errorSyntax("unexpected");

        // Should be at the top scope and every new var should be a FRef.  CNC:
        // slight possibility that stalling this error message will allow the
        // FRef to die after iter(), and thus avoid the error.
        for( int i=2; i<_scope._vars._len; i++ ) {
            Var v = _scope.var(i);
            FRefNode fref = (FRefNode)_scope._inputs.at(i); // Assert FRef
            assert v._fref && v._idx==i && fref._n==v;
            // Attempt to find external name, assuming it is a class name
            String xname = addClzPrefix(v._name);
            ExternNode ext = _code.findExternal(xname);
            if( ext == null )
                throw error("Undefined name '" + v._name + "'");
            // Define the FRef
            v.defFRef(ext._con,true,null);
            _scope.setDef(i,fref.addDef(ext));
        }

        // Clean up and reset
        _xScopes.pop();
        _scope.kill();
        _code._stop.peephole();

        // Close over all recursive types, and upgrade TYPES
        Ary<TypeStruct> ary = new Ary<>(TypeStruct.class);
        for( Type t : TYPES.values() )
            if( t instanceof TypeStruct ts )
                ary.add(ts);
        TypeStruct[] tss = Type.closeOver(ary.asAry(),TYPES);
        for( TypeStruct ts : tss )
            TYPES.put(ts._name,ts);

        // Walk over all Nodes, and upgrade the internal constants to the
        // closed-over types.
        _code._stop.walk( n -> n.upgradeType(TYPES) );

        // Gather top-level exported symbols
        for( Type t : TYPES.values() )
            if( t instanceof TypeStruct ts &&!ts.isAry() )
                _code._publishSymbols.add(ts);
    }



    /**
     *  Parses a function body, assuming the header is parsed.
     */
    private ReturnNode parseFunctionBody( TypeFunPtr sig, Lexer loc, String[] ids) {
        // Record & restore the existing scope vars.  The function parse might
        // update fields, so when the parse is done reset back to the pre-parse
        // state.
        Node[] preParse = _scope.save();

        // Parse function body normally
        ReturnNode ret = _parseFunctionBody(null, sig,loc,ids);

        // Reset all fields to pre-parse days
        _scope.restore(preParse);
        return ret;
    }


    // Parse a function body

    // Stack the {break,continue,return} scopes and a new lexical scope.
    // Build a FunNode and parameters, extending scope by parm names.
    // Parse the function body
    // - while( not end-of-function )
    // - - parseStatement
    // - - If isInit, also incrementally extend the self-type.
    // If isInit
    // - upgrade and close the self-type
    // - Gather fields and store values
    // - <init> returns self, not last
    // Unstack the {break,continue,return} scopes.

    private ReturnNode _parseFunctionBody( String funName, TypeFunPtr sig, Lexer loc, String[] ids) {
        boolean isInit = FunNode.isInit(funName);
        String typeName = isInit ? ((TypeMemPtr)sig._sig[0])._obj._name : null;

        // Stack parser state on the local Java stack, and unstack it later
        _scope.push(new Kind.Func(funName));
        ScopeNode    breakScope =    _breakScope;    _breakScope = null;
        ScopeNode continueScope = _continueScope; _continueScope = null;
        ScopeNode   returnScope =   _returnScope;   _returnScope = null;

        int oldUID = _code.UID(); // Used to approximate function size
        FunNode fun = (FunNode)peep(new FunNode(loc(),sig, funName, null,_code._start));
        // Once the function header is available, install in linker table -
        // allowing recursive functions.  Linker matches on declared args and
        // exact fidx, and ignores the return (because the fidx will only match
        // the exact single function).
        _code.link(fun);

        Node rpc = new ParmNode("$rpc",0,TypeRPC.BOT,fun,con(TypeRPC.BOT)).peephole();

        // Pre-call the function from Start, with worse-case arguments.  This
        // represents all the future, yet-to-be-parsed functions calls and
        // external calls.
        _scope.ctrl(fun);              // Scope control from function
        // Private mem alias tracking per function
        Node privMem = new ParmNode(ScopeNode.MEM0,1,TypeMem.BOT,fun,con(TypeMem.BOT)).peephole();
        MemMergeNode mem = new MemMergeNode(true,null,privMem);
        mem(mem);
        // All args, "as-if" called externally
        for( int i=0; i<ids.length; i++ ) {
            Type t = sig.arg(i);
            _scope.define(ids[i], t, i==0 && ids[0]=="self", new ParmNode(ids[i],i+2,t,fun,con(t)).peephole(), loc);
        }

        // Parse the function body.
        Node last=ZERO;         // Last statement as the default result
        while (!peek('}') && !_lexer.isEOF()) {
            // Parse a statement; record last statement as default return
            last = parseStatement();
            // For <init> and <clinit>, local vars are really struct fields and
            // get exposed as discovered.  This allows late local functions to
            // use early declared fields.
            if( isInit )
                updateSelfAsFieldsDiscovered( typeName );
        }

        ParmNode self = fun.parm(2);
        assert last!=null;
        if( isInit )
            last = initSpecialLastExpr(last, fun.isClz(), self);

        // Last expression is the return
        if( ctrl()._type==Type.CONTROL )
            addReturn(last);

        if( isInit )            // <init>  or  <clinit>
            upgradeSelfTypeAndStoreFields( typeName, fun.isClz(),
                                           self,
                                           fun.parm(3) );

        // Build a return from the _returnScope.
        // Can be no returns for never-exit functions
        Node rctl = _returnScope==null ? XCTRL            : _returnScope.ctrl().peephole();
        Node rmem = rctl==XCTRL        ? con(TypeMem.TOP) : _returnScope.mem ().merge().peephole();
        Node expr = rctl==XCTRL        ? con(Type.TOP)    : _returnScope._inputs.last().peephole();
        ReturnNode ret = (ReturnNode)peep(new ReturnNode(rctl, rmem, expr, rpc, fun));
        fun.setRet(ret);
        _code._stop.addDef(ret);
        // Approximate function size, for inlining heuristics
        fun._approxUIDs = _code.UID() - oldUID;

        // Unstack parser state
        if( _returnScope != null ) _returnScope.kill();
        _returnScope   =   returnScope;
        _continueScope = continueScope;
        _breakScope    =    breakScope;
        _scope.pop();

        return ret;
    }

    // TODO: Ponder another strategy here; the Actual Problem is later parsing
    // field loads/stores from pointers which will (eventually) become the
    // final Type of 'self' - they get an early version of 'self' missing
    // fields, and the parser wants to complain right now instead of allowing
    // unknown field loads against unknown types - and letting everything
    // resolve out later in SCCP.
    private void updateSelfAsFieldsDiscovered( String typeName ) {
        int lex = _scope.klast()._lexSize;
        assert _scope.var(lex)._name=="self";
        // Load self type in and out of the TYPES hashtable.
        TypeStruct tself = (TypeStruct)TYPES.get(typeName);
        // Start looking for newly declared vars at the lexical scope start,
        // skipping two ("self" and "selfmem") and fields seen already.
        for( int nvar = lex+2+tself._fields.length; nvar < _scope.nIns(); nvar++ ) {
            Var v = _scope.var(nvar);
            tself = tself.add(Field.make(v._name,v.type(),_code.nextALIAS(),v._final));
        }
        TYPES.put(typeName,tself);
    }


    Node initSpecialLastExpr( Node last, boolean isClz, Node self ) {
        if( !isClz ) {          // <init>, not <clinit>
            last = self;        // Init returns self, not last expression
        } else if( last._type instanceof TypeFunPtr tfp && tfp.nfcns()==1 ) {
            // Do not return a private function from a <clinit>, as these
            // might entirely inline and the TFP would otherwise be dead.
            // The <clinit> exit is only usable as the OS system exit result
            // or in simple tests.
            FunNode lastFun = _code.link(tfp);
            if( lastFun._name==null || lastFun._name.charAt(0)=='_' )
                last = ZERO;
        }
        return last;
    }

    void upgradeSelfTypeAndStoreFields( String typeName, boolean isClz, ParmNode self, ParmNode smem ) {
        int lex = _scope.klast()._lexSize;
        assert _scope.var(lex)._name=="self";
        // For init functions, upgrade any forward ref fields.
        TypeStruct tself = (TypeStruct)TYPES.get(typeName);
        int base = lex+2;       // Skip ctrl, mem
        assert base + tself.nkids() == _scope.nIns();
        for( int i=0; i<tself.nkids(); i++ ) {
            Var v = _scope.var(base +i);
            Field tfld = tself._fields[i];
            if( v._fref )
                tself = tself.remove(i);
            else if( v.type() != tfld._t )
                tself = tself.replace(tfld.makeFrom(v.type()));
        }

        // Close Struct type after parsing struct body.  Forward-ref fields
        // where placed in tself but will get promoted to an outer scope - and
        // should not be in tself.
        TYPES.put( tself._name, tself = tself.close() );

        // Improve self, selfMem types in scope.
        // Class self-type is a singleton pointer.
        self._con = self._type = TypeMemPtr.make((byte)2,tself,isClz);
        if( isClz ) {           // Upgrade class function signature
            FunNode fun = self.fun();
            fun.setSig(fun.sig().makeFrom(self._type,0));
        } else {                // <init> returns a upgraded private memory
            smem._con = smem._type = TypeMem.make(1,tself,true,false);
        }

        // When can _returnScope be null here? A never-exit constructor will
        // not have any returns, and thus no need to gather values and store
        // them into the (never) constructed object
        if( _returnScope==null )
            return;

        // A MemMerge to gather field updates as stores.  Classes update the
        // normal public memory.  <init> updates the private memory that got
        // passed in - is treated like a normal argument and not like memory.
        MemMergeNode mmm = isClz
            ? _returnScope.mem()                 // Public  memory update
            : new MemMergeNode(false,null,smem); // Private memory update


        // Store constructor results into fields
        int frefs=0;
        for( int i=0; i<tself.nkids(); i++ ) {
            Var v = _scope.var(base +i+frefs);
            if( v._fref )   // Forward refs not declared here
                frefs++;
            else {
                Node val = _returnScope.in(base + i);
                Field fld = tself._fields[i];
                Node stmem = mmm.alias(fld._alias);
                // Store value into extended struct
                Node st = peep(new StoreNode(null, fld._fname, fld._alias, fld._t, null, stmem, self, off(tself,fld._fname), val, fld._final));
                mmm.alias(fld._alias, st);
            }
        }

        if( !isClz )
            // Stuff private mem into normal expression return value.
            _returnScope.setDef(_returnScope.nIns()-1,mmm.peephole());
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
                throw TODO();
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
        default     -> throw TODO();
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
        default     -> throw TODO();
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
        if( !fside.equals("else") && (err=ret.err()) !=null ) throw err;
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
        return addReturn(expr);
    }
    private Node addReturn(Node expr) {
      expr.keep();
        // Need default memory, since it can be lazy, need to force
        // a non-lazy Phi
        mem().removeLazyAll();
        // For <init> and <clinit> - all fields in scope will be live-on-exit
        // and stored into `self` and need to have lazy-phi's inserted.
        int lexN = _scope.enclosingFuncOrDecl();
        Kind.Func k = (Kind.Func)_scope._kinds.at(lexN);
        if( FunNode.isInit(k._name) ) {
            int nestedLexSize = lexN+1 < _scope.depth() ? _scope._kinds.at(lexN+1)._lexSize : _scope.nIns();
            for( int i=k._lexSize; i<nestedLexSize; i++ )
                _scope.update(_scope.var(i),null);
            if( _returnScope != null ) {
                Var vexpr = _returnScope._vars.pop();
                Node oldX = _returnScope.removeLast();
                int rlen = _returnScope.nIns();
                while( rlen  < nestedLexSize ) {
                    _returnScope._vars.add(_scope.var(rlen));
                    _returnScope.addDef(Parser.con(_scope.var(rlen++).type().makeZero()));
                }
                _returnScope._vars.add(vexpr);
                _returnScope.addDef(oldX);
            }
        }

        // No prior merge point?  Just clone and hang on to it
        if( _returnScope == null ) {
            _returnScope = _scope.dup();
            while( lexN+1 < _returnScope.depth() )
                _returnScope._pop(); // Pop a nested block scope until we hit the function scope
            _returnScope.define("$expr", expr._type.glb(false), true, expr, null);

        } else {
            // For <init> and <clinit> - ALL FIELDS IN LAST SCOPE are live
            // and need to have lazy-phi's inserted.

            // ANd fields might not match.... will need matching Var/define with default values
            int rlen = _returnScope.nIns()-1;
            RegionNode r = ctrl(new RegionNode(null, null,_returnScope.ctrl(), _scope.ctrl()).init().keep());
            _returnScope.mem()._merge(mem(),r);
            _returnScope      ._merge(_scope,      r, rlen);
            Node oldExpr = _returnScope.in(rlen);
            _returnScope.setDef(rlen,new PhiNode("$expr", oldExpr._type.meet(expr._type), r, oldExpr, expr).peephole());
            _code.add(r);
            _returnScope.ctrl(r.unkeep());
        }
        ctrl(XCTRL);            // Kill control
        return expr.unkeep();
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
        // Just a plain expression, no assignment.
        // Distinguish `var==expr` from `var=expr`
        if( name==null || KEYWORDS.contains(name) || !matchOpx('=','=') )
            {  pos(old);  return parseExpression();  }

        // Find variable to update
        Var def = _scope.lookup(name);
        if( def==null )
            throw error("Undefined name '" + name + "'");

        // TOP fields are for late-initialized fields; these have never
        // been written to, and this must be the final write.  Other writes
        // outside the constructor need to check the final bit.
        if( _scope.in(def._idx)._type!=Type.TOP && def._final &&
            // Inside an allocation, final assign is OK, outside nope.
            // The alloc() call added the allocation scope
            !(_scope.inConstructor() && def._idx >= _scope.klast()._lexSize) )
            throw error("Cannot reassign final '"+name+"'");

        // Parse assignment expression
        Node expr = parseAsgn();

        // Lift expression, based on type
        Type decl = def.type(); // Declared type
        Node lift = liftExpr(expr.keep(), decl, true);
        // Update
        _scope.update(name,lift);
        // Return un-lifted expr
        return expr.unkeep();
    }

    // Make finals deep; widen ints to floats; narrow wide int types.
    // Early error if types do not match variable.
    private Node liftExpr( Node expr, Type t, boolean isLoad ) {
        // Auto-widen array to i64 (cast ptr to raw int bits)
        if( t == TypeInteger.BOT && expr._type instanceof TypeMemPtr tmp && tmp._obj.isAry() )
            expr = peep(new AddNode(peep(new CastNode(t,ctrl(),expr)),off(tmp._obj,"[]")));
        // Auto-widen int to float
        expr = widenInt( expr, t );
        // Auto-narrow wide ints to narrow ints.  For loads, emit code to force
        // the loaded value to match the declared sign/zero bits.  For stores,
        // just force the type, acting "as if" the store silently truncates.
        // CNC: Language design question: should these be OK or errors?
        //    byte b = 123456; // Currently silent, obviously sensible to error
        //    b++;             // Currently silent, but the math overflows and stores an int
        // CNC: Same issue for both, should storing an `int` into `byte` silently truncate or fail?
        Type et = expr._type;
        if( isLoad ) { expr = zsMask(expr,t); et = expr._type; }
        else if( et instanceof TypeInteger && t instanceof TypeInteger ) et=t;

        // Lift type to the declaration.  This will report as an error later if
        // we cannot lift the type.
        if( !et.isa(t) )
            expr = peep(new CastNode(t,null,expr));

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
            if( isExternDecl() ) {
                expr = externDecl(name,t);
                t = expr._type; // Upgrade declared type to the exact extern decl type
            } else {
                expr = parseAsgn();
            }
            // TOP means val and val is always final
            xfinal = (t==Type.TOP) ||
                expr instanceof ExternNode ||
                // BOTTOM is var and var is always not-final
                (t!=Type.BOTTOM &&
                 // no Bang AND
                 !hasBang &&
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

            // Final is deep on ptrs
            if( xfinal && t instanceof TypeMemPtr tmp ) {
                t = tmp.makeRO();
                expr = peep(new ReadOnlyNode(expr));
            }

            // expr is a constant function
            if( t instanceof TypeFunPtr && expr._type instanceof TypeFunPtr tfp && tfp.isConstant() ) {
                FunNode fun = _code.link(tfp);
                if( fun != null )
                    fun.setName(name); // Assign debug name to Simple function
            }

        } else {
            // Since no initializer, not-final but might need an initializer

            // Need an expression to infer the type.
            // Also, if not-null then need an initializing expression.
            // Allowed in a class def, because type/init will happen in the constructor.
            if( (inferType || (t instanceof TypeNil tn && !tn.nullable() )) && !_scope.inConstructor() )
                throw errorSyntax("=expression");
            // Initial value for uninitialized struct fields.
            expr = switch( t ) {
                // Nullable pointers get a NIL; not-null get a BOTTOM which
                // signals that they *must* be initialized in the constructor.
            case TypeNil tn -> tn.nullable() ? NIL : con(Type.BOTTOM);
            case TypeInteger ti -> ZERO;
            case TypeFloat tf -> con(TypeFloat.FZERO);
            // Bottom signals type inference: they must be initialized in
            // the constructor and that's when we'll discover the type.
            case Type tt -> { assert tt==Type.BOTTOM; yield con(tt); }
            };
            // Nullable fields are set in the constructor, but remain shallow final.
            // e.g. final pointer to a read/write array
            if( t instanceof TypeNil tn && !tn.nullable() && !hasBang )
                fld_final = true;
        }

        // Lift expression, based on type
        Node lift = liftExpr(expr, t, true);

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
    private Node parseStruct( ) {
        // keyword "struct" already parsed, so expect the struct name next.
        String typeName = requireId();
        require("{");

        // create type class:NESTED.typeName; no fields.

        String old = _nestedType;
        typeName = (old+"."+typeName).intern();
        _nestedType = typeName; // Recursive structs start with this as their basename

        ReturnNode ret = parseStruct( false, typeName );

        _nestedType = old;

        // Insert a field into class:NESTED.typeName of "val typeName = fcn-ptr-to-init"
        // Function does malloc internally.
        // Future self: means subtyping has to deal with "who does the malloc"
        TypeFunPtr tfp = ret.fun().sig();
        TypeStruct ts = TypeStruct.make(addClzPrefix(typeName),false,Field.make(typeName,tfp,_code.nextALIAS(),true));
        TYPES.put(ts._name,ts);

        require("}");
        return require(ZERO,";");
    }

    // Parse a struct declaration (not an allocation); file-level is a class-init, and scope structs are normal
    private ReturnNode parseStruct( boolean isClz, String typeName ) {
        // Record & restore global state set during parsing the <init> code
        Node oldCtrl= _scope.ctrl().keep();
        Node oldMem = _scope.mem ().keep();

        // Make the future clazz/instance struct.
        TypeStruct tself = TypeStruct.make(typeName,true);
        TYPES.put(typeName, tself);

        // <cl/init> signature: { self arg/selfMem -> BOT/selfMem }
        // Self-memory is the rare *private* (never aliased) memory for the new object.
        TypeMem privMem = isClz ? null : TypeMem.make( 1, tself, true, false );
        Type targ = isClz ? TypeInteger.BOT : privMem;
        Type tret = isClz ? Type.BOTTOM     : privMem;
        Type[] targs = new Type[]{ TypeMemPtr.make(tself), targ };
        TypeFunPtr sig = TypeFunPtr.make1((byte)2, false, targs, tret, _code.nextFIDX());

        String fname = (typeName + (isClz ? ".<clinit>" : ".<init>")).intern();
        String[] ids = new String[]{"self", isClz ? "arg" : "#selfMem"};

        // Struct decls look like function bodies.  Parse function body normally.
        ReturnNode ret = _parseFunctionBody(fname,sig,loc(),ids);
        // Unwind global state.
        ctrl(oldCtrl.unkeep());
        mem (oldMem .unkeep());
        return ret;
    }


    // Parse and return a type or null.  Valid types always are followed by an
    // 'id' which the caller must parse.  This lets us distinguish forward ref
    // types (which ARE valid here) from local vars in an (optional) forward
    // ref type position.

    // t = int|i8|i16|i32|i64|u8|u16|u32|u64|byte|bool | flt|f32|f64 | val | var | struct[?]

    // Structs have nested names, and can be discovered with a partial name
    // match.  Example, searching for a type name "C.D" in the namespace "A.B".
    // This will match "A.B.C.D", then "A.C.D", then "C.D".
    private Type type() {
        // Only type with a leading `{` is a function pointer...
        if( peek('{') ) return typeFunPtr();

        // Otherwise you get a type name
        int old1 = pos();
        String tname = _lexer.matchId();
        if( tname==null ) return null;

        // Convert the type name to a type.
        Type t0 = TYPES.get(tname), t1 = t0;
        // No new types as keywords
        if( KEYWORDS.contains(tname) ) {
            if( t0 == null ) return posT(old1);
            // Something like "int" or "f64" or "var".
            // Can be "int[]" so still need to check array-ness
        } else {
            if( t0 == Type.BOTTOM || t0 == Type.TOP ) return t0; // var/val type inference
            // Now have a tname like "C", which might be followed by more ".D"
            // typenames.  Do a search for e.g. "A.B.C", then "A.C", then "C".
            String nest = _nestedType, fullq;
            while( true ) {
                fullq = nest+"."+tname; // Full qualified name
                t0 = TYPES.get(fullq);
                if( t0 != null )
                    break;          // Search succeeded
                // Subtract a layer from nested typenames
                int idx = nest.lastIndexOf('.');
                if( idx== -1 ) break; // Search failed
                nest = nest.substring(0,idx);
            }
            tname = fullq;          // Fully qualified type name

            // Gather id.id.id.lasttype.
            // Ok (and normal) for "id.id.id" to be empty.
            while( true ) {
                int old2 = pos();
                if( !match(".") )  break;
                String sname = _lexer.matchId();
                if( sname==null ) { pos(old2); break; }
                sname = tname+"."+sname;
                if( TYPES.get(sname) == null ) { pos(old2); break; }
            }
            tname = tname.intern();
            // Still no type found?  Assume forward reference
            t1 = t0 == null
                ? TypeStruct.make(tname,true) // Null: assume a forward ref type
                : t0;
        }

        // Structs are always actually references
        if( t1 instanceof TypeStruct ts1 )
            t1 = TypeMemPtr.make(ts1);

        // Nest arrays and '?' as needed
        Type t2 = t1;
        while( true ) {
            if( match("?") ) {
                if( t2 instanceof TypeMemPtr tmp ) {
                    if( tmp.nullable() ) throw error("Type "+t2+" already allows null");
                    t2 = tmp.makeNullable();
                } else
                    throw error("Type "+t0+" cannot be null");
            } else if( match("[~]") ) {
                t2 = TypeMemPtr.make(typeAry(t2,true));
            } else if( match("[]") ) {
                t2 = TypeMemPtr.make(typeAry(t2,false));
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
        TYPES.put(tname,((TypeMemPtr)t1)._obj);
        // Return the (array, final) type
        return t2;
    }

    // Make an array type of t.  Always record a mutable version,
    // but return the requested version.
    private TypeStruct typeAry( Type t, boolean efinal ) {
        if( t instanceof TypeMemPtr tmp && tmp.notNull()  )
            throw error("Arrays of reference types must always be nullable");
        String tname = ("[]"+t.str()).intern();
        TypeStruct ta = (TypeStruct)TYPES.get(tname);
        if( ta==null ) {
            ta = TypeStruct.makeAry(tname,TypeInteger.U32,_code.nextALIAS(),t,_code.nextALIAS(), false );
            TYPES.put(tname,ta);
        }
        if( !efinal ) return ta;
        // Already have the aliases, just efinal is wrong
        return TypeStruct.makeAry(tname,TypeInteger.U32,ta._fields[0]._alias,t,ta._fields[1]._alias,true );
    }

    // A function type is `{ type... -> type }` or `{ type }`.
    private Type typeFunPtr() {
        int old = pos();        // Record lexer position
        match("{");             // Skip already-peeked '{'
        Type t0 = type();       // Either return or first arg
        if( t0==null ) return posT(old); // Not a function
        Ary<Type> ts = new Ary<>(Type.class);
        // Push 'self' arg unless at file scope
        if( _scope.inConstructor() )
            ts.push(TypeMemPtr.make((TypeStruct)TYPES.get(_nestedType)));
        if( match("}") )                 // No-arg function { -> type }
            return TypeFunPtr.make(match("?"),false,ts.asAry(),t0);
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
        int dir = parseCompDir();
        // No compare
        if( dir==0 ) return lhs;

        // Compare
        Node rhs = parseShift().keep();
        Node cmp = makeCompBool(dir,lhs,rhs); // Convert to a bool

        // Stacked compares?
        int dir0 = parseCompDir();
        if( dir0 == 0 ) {
            rhs.unkeep();
            if( rhs != cmp ) rhs.isKill();
            return cmp;
        }

        // rhs is keeped() and becomes lhs
        // cmp is NOT keeped() and is the last test
        Node ifNode = new IfNode(ctrl(), cmp).peephole();
        Node ifT = new CProjNode(ifNode.  keep(), 0, "True" ).peephole();
        Node ifF = new CProjNode(ifNode.unkeep(), 1, "False").peephole();
        // False side does nothing but capture memory & side-effects
        ScopeNode fail = _scope.dup();
        fail.ctrl(ifF);

        // Loop over stacked compares.  Each loop moves the old RHS to become
        // the new LHS, so e.g. (a < b < c < d) compares (LHS:a < RHS:b) then
        // compares (LHS:b < RHS:c) then (LHS:c < RHS:d), etc.
        while( dir0 != 0 ) {
            if( Math.abs(dir) != Math.abs(dir0) )
                throw error("Mixing relational directions in a chained relational test");
            // True side parses next arm of test
            ctrl(ifT);
            lhs = rhs;          // lhs is keeped() and is old RHS
            rhs = parseShift().keep();
            cmp = makeCompBool(dir,lhs.unkeep(),rhs); // Convert to a bool
            ifNode = new IfNode(ctrl(), cmp).peephole();
            ifT = new CProjNode(ifNode.  keep(), 0, "True" ).peephole();
            ifF = new CProjNode(ifNode.unkeep(), 1, "False").peephole();
            // Merge result into the fail case
            ctrl(ifF);
            fail.mergeScopes(_scope.dup(), loc()).peephole();
            // Check next stacked compare
            dir0 = parseCompDir();
        }
        rhs.unkill();
        ctrl(ifT);
        RegionNode r = fail.mergeScopes(_scope, loc()).init();
        _scope = fail;
        return new PhiNode("",TypeInteger.BOOL,r,ZERO,con(1)).peephole();
    }

    private int parseCompDir() {
        if( match("<=") ) return -1; // Negative for "equals-or-less"
        if( match(">=") ) return -2; // 1 for <, 2 for >
        if( match("<" ) ) return  1; // Positive for just "less"
        if( match(">" ) ) return  2;
        return 0;
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
            int delta = _lexer.peek(-1)=='+' ? 1 : -1; // Inc vs Dec
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
     *                   type['.' Id]*
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

        int pos = pos();
        // Expect an identifier now
        String id = _lexer.matchId();
        if( id == null || KEYWORDS.contains(id) )
            throw errorSyntax("an identifier or expression");
        int pos2 = pos();
        // Attempt a local var lookup first
        Var var = _scope.lookup(id);
        if( var==null ) {
            // Not a local var, try again as a type name
            pos(pos);
            Type t = type();
            if( t!=null ) {
                // Static <clinit> typename lookup
                if( peek('.') ) // type.fld, which is really a field lookup in the class object
                    return parsePostfix(con(t));
                // Not a local var, but yes a bare type... disallow a local
                // FREF and return null, not-a-primary parse.
                pos(pos);
                return null;
            }

            // Insert FREF outside enclosing Kind.Func scope.
            pos(pos2);
            var = _scope.defineFRef(id,loc());
        }

        // Load local value
        Node rvalue = _scope.in(var);
        if( rvalue._type == Type.BOTTOM )
            if( rvalue instanceof FRefNode )
                return parsePostfix(rvalue); // No methods on FRefs right now
            else throw error("Cannot read uninitialized field '"+id+"'");

        // Check for a instance field load
        int kx = _scope.kindx(var); // Declaration scope
        int fx = _scope.enclosingFunction(); // Enclosing function scope
        Kind kk = _scope._kinds.at(kx);
        Kind fk = _scope._kinds.at(fx);
        // Access instance field from 'self'
        if( kx+1 == fx && kk instanceof Kind.Func inst && FunNode.isInstance(inst._name) && fk instanceof Kind.Func method )
            return parsePostfixName(_scope.in(method._lexSize),var._name);

        // Check for a function-escaping variable; these require true
        // closures.  Final constants are OK; final vars require a hidden var
        // argument; not-final fields can just use an explicit struct arg.
        if( !(var._final && rvalue._type.isConstant()) &&
            kx > 0  && // Global scope is OK, only ever one of these (not one per function invoke)
            kx < fx )  // Out of scope
            throw error("Variable '"+var._name+"' is out of function scope and must be a final constant");

        // Check for assign-update, x += e0;
        char ch = _lexer.matchOperAssign();
        if( ch==0  ) {          // Normal primary, check for postfix updates
            int selfx = _scope.kindFcn(var)._lexSize;
            // Might be a method call from inside a method, so no explicit "self".
            // Pass the in-scope "self"
            return parsePostfixMethod(rvalue,_scope.in(selfx));
        }
        // Assign-update direct into Scope
        Node op = opAssign(ch,rvalue, var.type() );
        _scope.update(var,op);
        return postfix(ch) ? rvalue.unkeep() : op;
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
        default  -> throw TODO();
        };
        // Convert to float ops, or narrow int types; error if not declared type.
        // Also, if postfix LHS is still keep()
        return liftExpr(peep(op.widen()),t.glb(false),true);
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
                throw error("Cannot allocate a non-nullable, since arrays are always zero/null filled");
            Node len = parseAsgn().keep();
            if( !(len._type instanceof TypeInteger) )
                throw error("Cannot allocate an array with length "+len._type);
            require("]");
            return allocArray(typeAry(t,false),len);
        } else {
            if( !(t instanceof TypeMemPtr tmp) )
                throw error("Cannot allocate a "+t.str());
            return allocStruct(tmp._obj);
        }
    }

    private Node allocStruct(TypeStruct ts) {
        Node size = off(ts, " len");

        // Build a NewNode; takes in ctrl and size.
        // Produces a ptr and a private mem.
        NewNode nnn = new NewNode(ts, ctrl(), size ).init();
        ProjNode self = new ProjNode(nnn,0,ts.str()).init().keep();
        ProjNode smem = new ProjNode(nnn,1,"#selfMem").init();

        // Find a "class:XXX" struct, with field "XXX" function ptr as the <init>
        TypeStruct clz = (TypeStruct)TYPES.get(addClzPrefix(ts._name));
        if( clz==null )
            throw error("Unknown struct type '" + ts._name + "'");
        TypeFunPtr init = (TypeFunPtr)clz.field(ts._name)._t;

        // Call construct <init>($ctrl,$mem,NewNode.self,NewNode.#selfMem) and
        // encourage inlining
        Ary<Node> args = new Ary<>(Node.class){{add(ctrl()); add(mem().merge()); add(self); add(smem); add(con(init)); }};
        Node selfMem = functionCall( args ).keep();
        // The returned value is a merge of private *Memory* and NOT some Scalar

        // Check for constructor block:
        boolean hasConstructor = match("{");
        if( hasConstructor ) {
            int idx = _scope.nIns();
            // Push a scope, and pre-assign all struct fields.
            _scope.push(new Kind.Func((ts._name+".<init>").intern()));
            Lexer loc = loc();
            for( Field fld : ts._fields ) {
                Node finit;
                // Speed optimization: skip load-of-merge-of-store
                if( selfMem instanceof MemMergeNode mmm && mmm.alias(fld._alias) instanceof StoreNode st )
                    finit = st.val();
                else
                    finit = peep(new LoadNode(null, fld._fname, fld._alias, fld._t, null, selfMem, self, off(ts, fld._fname)));
                _scope.define(fld._fname, fld._t, fld._final, finit, loc);
            }

            // Parse the constructor body
            while( !peek('}') && !_lexer.isEOF() )
                parseStatement();
            require("}");

            // Store updated fields
            MemMergeNode updatedPrivMem = new MemMergeNode(false);
            for( Field fld : ts._fields ) {
                Node newval = _scope.in(idx++);
                // Speed optimization: skip store-of-same
                if( !(selfMem instanceof MemMergeNode mmm && mmm.alias(fld._alias) instanceof StoreNode st && st.val() == newval) ) {
                    Node st = peep(new StoreNode(null, fld._fname, fld._alias, fld._t, null, selfMem, self, off(ts, fld._fname), newval, true));
                    updatedPrivMem.alias(fld._alias,st);
                }
            }
            updatedPrivMem.alias(1,selfMem.unkeep());
            selfMem = peep(updatedPrivMem).keep();
        }

        // Might be TOP if parsing in dead/unreachable code
        if( !(selfMem._type instanceof TypeMem tmem) ) {
            selfMem.unkeep();
            return self.unkeep();
        }

        // Check that all fields are initialized
        TypeStruct postinit = (TypeStruct)(tmem._t);
        for( Field fld : postinit._fields ) {
            assert fld._t != Type.TOP;
            if( fld._t == Type.BOTTOM )
                throw error("'"+postinit._name+"' is not fully initialized, field '" + fld._fname + "' needs to be set in a constructor");
        }
        // Pop constructor scope
        if( hasConstructor )
            _scope.pop();

        // Escape all new aliases.  EscapeNode inputs are the self pointer, the
        // merged private memory, then all the named public aliases.  The
        // output is all the newly merged public aliases - but not actually
        // bulk memory.
        for( Field fld : postinit._fields ) {
            Node esc = peep(new EscapeNode((Field)fld.glb(true),self,selfMem,memAlias(fld._alias)));
            memAlias(fld._alias,esc);
        }

        selfMem.unkill();
        return self.unkeep();
    }

    private Node allocArray(TypeStruct ts, Node len) {
        Field  lenFld = ts.field("#" );
        Field bodyFld = ts.field("[]");

        ConFldOffNode base = off(ts, "[]");
        int scale = ts.aryScale();
        Node size = peep(new AddNode(base,peep(new ShlNode(null,len,con(scale)))));

        // Build a NewNode; takes in and puts out all aliases.
        NewNode nnn = new NewNode(ts, ctrl(), size ).init();
        ProjNode self = new ProjNode(nnn,0,ts.str()).init().keep();
        ProjNode smem = new ProjNode(nnn,1,"#selfMem").init().keep();

        // Store length.  Rest of array is zero'd via CALLOC during CodeGen.
        // Length is casted to sanity.
        // TODO: Needs runtime check.
        Node st = peep(new StoreNode(null, "#", lenFld._alias, TypeInteger.U32, null, smem, self, off(ts,"#"), len.unkeep(), true));

        // TODO: Allow add-on "constructor" to init the array elements

        memAlias( lenFld._alias,peep(new EscapeNode( lenFld,self,st  ,memAlias( lenFld._alias))));
        memAlias(bodyFld._alias,peep(new EscapeNode(bodyFld,self,smem,memAlias(bodyFld._alias))));
        smem.unkeep();
        return self.unkeep();
    }

    private Node newString(String s) {
        TypeStruct ts = typeAry(TypeInteger.U8,true);
        int  lenAlias = ts.field("#" )._alias;
        int elemAlias = ts.field("[]")._alias;
        TypeConAryB body = TypeConAryB.make(s);
        TypeInteger slen = TypeInteger.constant(s.length());
        // Make a TMP, not-null (byte)2, singleton (true) (requires text
        // strings are hash-interned), with a TypeStruct having a constant
        // array body.
        TypeMemPtr str = TypeMemPtr.make((byte)2,TypeStruct.makeAry("[]u8", slen, lenAlias, body, elemAlias, true),true);
        assert str.isConstant();
        return con(str);
    }

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
    // 'self' for a possible method call
    private Node parsePostfixMethod(Node expr, Node self) {
        // Self method call?
        // - expr is a TFP, with a potential self,
        // - - which is neither a Class nor an array
        // - has function call args following
        if( expr._type instanceof TypeFunPtr tfp && tfp._sig.length>=1 && tfp._sig[0] instanceof TypeMemPtr tself &&
            // Not an array or Class method
            !(tself._obj._name.startsWith( "[" ) ||
              (self._type instanceof TypeMemPtr clzptr && startsClzPrefix(clzptr._obj._name))) &&
            match("(") ) {
            expr = parsePostfix(require(functionCall(expr,self),")"));
        } else if( self != null )
            self.isKill();      // Not a method call, no need for self
        return parsePostfix(expr);
    }
    private Node parsePostfix(Node expr) {
        String name;
        if( match(".") )      name = requireId();
        else if( match("#") ) name = "#";
        else if( match("[") ) name = "[]";
        // can get here without a 'self': "self.method()(next)(next)(next)"
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
        // Keep expr across possible updates
        expr.keep();

        // TODO: optimize a mix of known and unknown mem stores
        // Do we know the field type already?
        Field fld = expr._type instanceof TypeMemPtr tmp ? tmp._obj.field(name) : null;
        // With no field, we will update the bulk memory
        int alias = fld==null ?  1            : fld._alias;
        Type decl = fld==null ? Type.BOTTOM   : fld._t;

        // Field offset; fixed for structs, computed for arrays
        Node off = (name=="[]"
            // Array element math, with unknown base offset and shift amount.
            ? peep(new AddNode(fldoff(expr,"[]"),peep(new ShlNode(null,require(parseAsgn(),"]"),fldoff(expr,"<<")))))
            // Named field offset on an unknown struct type
            : fldoff(expr,name)).keep();

        // Disambiguate "obj.fld==x" boolean test from "obj.fld=x" field assignment
        if( matchOpx('=','=') ) {
            // Field assignment
            Node val = parseAsgn().keep();
            // Lift value for store
            Node lift = new LiftNode(expr,name,val,false).peephole();
            // Memory for store, post assignment expression
            Node mem = fld==null ? mem().merge() : memAlias(fld._alias);
            // Store to field
            Node st = new StoreNode(loc(), name, alias, decl, ctrl(), mem, expr.unkeep(), off.unkeep(), lift, false).peephole();

            // For well known types, we can use sharp alias updates right now
            if( fld != null )  memAlias(fld._alias, st);
            // For unknown / forward-ref types, we default to the conservative
            // alias#1, but this should clean up later as types are discovered.
            else mem(new MemMergeNode(true,null,st));

            return val.unkeep();        // "obj.a = expr" returns the expression while updating memory
        }

        // Memory for load
        Node mem = fld==null ? mem().merge() : memAlias(fld._alias);
        // Loading from a constant array, the declared type is the
        // meet-over-elements and not the array itself.
        if( name=="[]" && decl instanceof TypeConAry conary )
            decl = conary.elem();

        // Load field
        Node load = peep(new LoadNode(loc(),name, alias, decl, ctrl(), mem, expr, off));

        // Check for assign-update, "ptr.fld += expr" or "ary[idx]++"
        char ch = _lexer.matchOperAssign();
        if( ch!=0 ) {
            if( decl == Type.BOTTOM ) throw TODO("Need LiftNode after opAssign");
            Node op = opAssign(ch,load, decl );
            Node st = new StoreNode(loc(), name, alias, decl.glb(true), ctrl(), mem, expr.unkeep(), off.unkeep(), op, false).peephole();
            // For well known types, we can use sharp alias updates right now
            if( fld != null )  memAlias(fld._alias, st);
            // For unknown / forward-ref types, we default to the conservative
            // alias#1, but this should clean up later as types are discovered.
            else mem(new MemMergeNode(true,null,st));

            load = postfix(ch) ? load.unkeep() : op;
            // And use the original loaded value as the result
            return load;
        }
        off.unkill();
        // Might be a method call, so pass expr as 'self'
        return parsePostfixMethod(load,expr.unkeep());
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
        TypeStruct self = (TypeStruct)TYPES.get(_nestedType);
        if( self != null ) {
            ts.push( TypeMemPtr.make( self ) ); // Self pointer
            ids.push( "self" );
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
        fcn.keep();            // Keep while parsing args

        Ary<Node> args = new Ary<>( Node.class );
        args.push(null);        // Space for ctrl,mem
        args.push(null);
        if( self != null )      // Method call has a self
            args.push(self.keep());
        while( !peek(')') ) {
            Node arg = parseAsgn();
            if( arg==null ) break;
            args.push(arg.keep());
            if( !match(",") ) break;
        }
        // Control & memory after parsing args
        args.set(0,ctrl().keep());
        args.set(1,mem().merge().keep());
        args.push(fcn);        // Function pointer
        // Unkeep them all
        for( Node arg : args )
            arg.unkeep();
        return functionCall(args);
    }

    private Node functionCall( Ary<Node> args ) {
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
        cend = (CallEndNode)cend.keep().peephole(); // TODO: Might inline
        // Control from CallEnd
        ctrl(new CProjNode(cend,0,ScopeNode.CTRL).peephole());
        // Memory from CallEnd
        Node mem = new ProjNode(cend,1,ScopeNode.MEM0).peephole();
        mem(new MemMergeNode(true,null,mem));
        // Call result
        return new ProjNode(cend.unkeep(),2,"#2").peephole();
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
    private ConstantNode externDecl( String ex, Type t ) {
        if( t instanceof TypeFunPtr tfp ) { // Generic TFP from type parse
            tfp = _code.makeFun(tfp);       // Get a FIDX, becomes a constant
            _code.externFunc(tfp.fidx(),ex);  // Map fidx to extern name
            return con(tfp);
        }
        return (ExternNode)(new ExternNode(t,ex).peephole());
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
    // Field offset for a known type (but unknown offset)
    public static ConFldOffNode off( TypeStruct ts, String fname ) {
        return (ConFldOffNode)(new ConFldOffNode(ts,fname).peephole());
    }
    // Field offset for an unknown type
    public Node fldoff( Node n, String name ) {
        return new FldOffNode(n,name,loc()).peephole();
    }
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

    private boolean noLowerCase(String s) {
        for( int i=0; i<s.length(); i++ )
            if( Character.isLowerCase(s.charAt(i)) )
                return false;
        return true;
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
            if (isEOF()) return "EOF";
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
            return new String(_input, start, --_position - start).intern();
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

    ParseException errorSyntax(String syntax) { return _errorSyntax("expected `"+syntax+"`");  }
    private ParseException _errorSyntax(String msg) {
        return error("Syntax error, "+msg+" but found `" + _lexer.getAnyNextToken()+"`");
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
