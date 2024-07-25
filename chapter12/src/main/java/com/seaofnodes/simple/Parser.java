package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

import java.util.*;

/**
 * The Parser converts a Simple source program to the Sea of Nodes intermediate
 * representation directly in one pass. There is no intermediate Abstract
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

    public static ConstantNode ZERO;
    public static XCtrlNode XCTRL;

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
            add("break");
            add("continue");
            add("else");
            add("false");
            add("if");
            add("int");
            add("new");
            add("null");
            add("return");
            add("struct");
            add("true");
            add("while");
        }};


    /**
     * We clone ScopeNodes when control flows branch; it is useful to have
     * a list of all active ScopeNodes for purposes of visualization of the SoN graph
     */
    public final Stack<ScopeNode> _xScopes = new Stack<>();

    ScopeNode _continueScope;
    ScopeNode _breakScope;

    // Mapping from a Struct name to a Struct.
    public static Map<String, TypeStruct> OBJS = new HashMap<>();

    public Parser(String source, TypeInteger arg) {
        Node.reset();
        IterPeeps.reset();
        OBJS.clear();
        SCHEDULED = false;
        _lexer = new Lexer(source);
        _scope = new ScopeNode();
        _continueScope = _breakScope = null;
        START = new StartNode(new Type[]{ Type.CONTROL, arg });
        STOP = new StopNode(source);
        ZERO = new ConstantNode(TypeInteger.constant(0)).peephole().keep();
        XCTRL= new XCtrlNode().peephole().keep();
    }

    public Parser(String source) {
        this(source, TypeInteger.BOT);
    }

    @Override
    public String toString() { return _lexer.toString(); }

    String src() { return new String( _lexer._input ); }

    // Debugging utility to find a Node by index
    public static Node find(int nid) { return START.find(nid); }

    private Node ctrl() { return _scope.ctrl(); }

    private Node ctrl(Node n) { return _scope.ctrl(n); }

    public StopNode parse() { return parse(false); }
    public StopNode parse(boolean show) {
        _xScopes.push(_scope);
        // Enter a new scope for the initial control and arguments
        _scope.push();
        _scope.define(ScopeNode.CTRL, Type.CONTROL   , new CProjNode(START, 0, ScopeNode.CTRL).peephole());
        _scope.define(ScopeNode.ARG0, TypeInteger.BOT, new  ProjNode(START, 1, ScopeNode.ARG0).peephole());
        parseBlock();
        if( ctrl()._type==Type.CONTROL )
            STOP.addReturn(new ReturnNode(ctrl(), new ConstantNode(TypeInteger.constant(0)).peephole(), _scope).peephole());
        _scope.pop();
        _xScopes.pop();
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
    private Node parseBlock() {
        // Enter a new scope
        _scope.push();
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
        else if (matchx("int")     ) return parseDecl(TypeInteger.BOT);
        else if (matchx("flt")     ) return parseDecl(TypeFloat  .BOT);
        else if (match ("{")       ) return require(parseBlock(),"}");
        else if (matchx("if")      ) return parseIf();
        else if (matchx("while")   ) return parseWhile();
        else if (matchx("break")   ) return parseBreak();
        else if (matchx("continue")) return parseContinue();
        else if (matchx("struct")  ) return parseStruct();
        else if (matchx("#showGraph")) return require(showGraph(),";");
        // declarations of vars with struct type are handled in parseExpressionStatement due
        // to ambiguity
        else return parseExpressionStatement();
    }

    /**
     * Parse a struct field.
     * <pre>
     *     int IDENTIFIER ;
     * </pre>
     */
    private Field parseField(String sname) {
        if( matchx("int") )     // Currently only parsing "int" type fields
            return require(Field.make(requireId().intern(),TypeInteger.BOT),";");
        throw errorSyntax("A field type is expected, only type 'int' is supported at present");
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
        if ( OBJS.containsKey(typeName)) throw errorSyntax("struct '" + typeName + "' cannot be redefined");
        ArrayList<Field> fields = new ArrayList<>();
        require("{");
        while (!peek('}') && !_lexer.isEOF()) {
            Field field = parseField(typeName);
            if (fields.contains(field)) throw errorSyntax("Field '" + field + "' already defined in struct '" + typeName + "'");
            fields.add(field);
        }
        require("}");
        // Build and install the TypeStruct
        TypeStruct ts = TypeStruct.make(typeName, fields);
        OBJS.put(typeName, ts); // Insert the struct name in the collection of all struct names
        START.addMemProj(ts, _scope); // Insert memory edges
        return parseStatement();
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

        var savedContinueScope = _continueScope;
        var savedBreakScope    = _breakScope;

        require("(");

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
        var pred = require(parseExpression(), ")");
        // IfNode takes current control and predicate
        Node ifNode = new IfNode(ctrl(), pred).peephole();
        // Setup projection nodes
        Node ifT = new CProjNode(ifNode.  keep(), 0, "True" ).peephole().keep();
        Node ifF = new CProjNode(ifNode.unkeep(), 1, "False").peephole();

        // Clone the body Scope to create the break/exit Scope which accounts for any
        // side effects in the predicate.  The break/exit Scope will be the final
        // scope after the loop, and its control input is the False branch of
        // the loop predicate.  Note that body Scope is still our current scope.
        ctrl(ifF);
        _xScopes.push(_breakScope = _scope.dup());

        // No continues yet
        _continueScope = null;

        // Parse the true side, which corresponds to loop body
        // Our current scope is the body Scope
        ctrl(ifT.unkeep());     // set ctrl token to ifTrue projection
        parseStatement();       // Parse loop body

        // Merge the loop bottom into other continue statements
        if (_continueScope != null) {
            _continueScope = jumpTo(_continueScope);
            _scope.kill();
            _scope = _continueScope;
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
        return _scope = exit;
    }

    private ScopeNode jumpTo(ScopeNode toScope) {
        ScopeNode cur = _scope.dup();
        ctrl(XCTRL); // Kill current scope
        // Prune nested lexical scopes that have depth > than the loop head
        // We use _breakScope as a proxy for the loop head scope to obtain the depth
        while( cur._scopes.size() > _breakScope._scopes.size() )
            cur.pop();
        // If this is a continue then first time the target is null
        // So we just use the pruned current scope as the base for the
        // "continue"
        if (toScope == null)
            return cur;
        // toScope is either the break scope, or a scope that was created here
        assert toScope._scopes.size() <= _breakScope._scopes.size();
        toScope.ctrl(toScope.mergeScopes(cur));
        return toScope;
    }

    private void checkLoopActive() { if (_breakScope == null) throw Parser.error("No active loop for a break or continue"); }

    private Node parseBreak   () { checkLoopActive(); return (   _breakScope = require(jumpTo(    _breakScope ),";"));  }
    private Node parseContinue() { checkLoopActive(); return (_continueScope = require(jumpTo( _continueScope ),";"));  }

    /**
     * Parses a statement
     *
     * <pre>
     *     if ( expression ) statement [else statement]
     * </pre>
     * @return a {@link Node}, never {@code null}
     */
    private Node parseIf() {
        require("(");
        // Parse predicate
        var pred = require(parseExpression(), ")").keep();
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
        _scope.upcast(ifT,pred,false); // Up-cast predicate
        parseStatement();       // Parse true-side
        ScopeNode tScope = _scope;

        // Parse the false side
        _scope = fScope;        // Restore scope, then parse else block if any
        ctrl(ifF.unkeep());     // Ctrl token is now set to ifFalse projection
        if (matchx("else")) {
            _scope.upcast(ifF,pred,true); // Up-cast predicate
            parseStatement();
            fScope = _scope;
        }
        pred.unkeep();

        if( tScope.nIns() != ndefs || fScope.nIns() != ndefs )
            throw error("Cannot define a new name on one arm of an if");

        // Merge results
        _scope = tScope;
        _xScopes.pop();       // Discard pushed from graph display

        return ctrl(tScope.mergeScopes(fScope));
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

    /**
     * Parses an expression statement or a declaration statement where type is a struct
     *
     * <pre>
     *      name;         // Error
     * type name;         // Define name with default initial value
     * type name = expr;  // Define name with given   initial value
     *      name = expr;  // Reassign existing
     *             expr   // Something else
     * </pre>
     * @return an expression {@link Node}, never {@code null}
     */
    private Node parseExpressionStatement() {
        int old = _lexer._position;
        Type t = type();
        String name = requireId();
        Node expr;
        if( match(";") ) {      // Assign a default value
            // No type and no expr is an error
            if( t==null ) throw errorSyntax("expression");
            expr = new ConstantNode(t.makeInit()).peephole();
        } else if( match("=") ) { // Assign "= expr;"
            expr = require(parseExpression(), ";");
        } else {                // Neither, so just a normal expression parse
            _lexer._position = old;
            return require(parseExpression(),";");
        }

        // Defining a new variable vs updating an old one
        if( t != null ) {
            if( _scope.define(name,t,expr) == null )
                throw error("Redefining name '" + name + "'");
        } else {
            Node n = _scope.lookup(name);
            t = _scope.lookupDeclaredType(name);
            if( n==null )
                throw error("Undefined name '" + name + "'");
            _scope.update(name,expr);
        }
        if( expr._type instanceof TypeInteger && t instanceof TypeFloat )
            expr = expr.widen();
        if( !expr._type.isa(t) )
            throw error("Type " + expr._type.str() + " is not of declared type " + t.str());
        return expr;
    }

    // Parse a type-or-null
    private Type type() {
        int old = _lexer._position;
        String tname = _lexer.matchId();
        if( tname==null ) return null;
        if( tname.equals("int") ) return TypeInteger.BOT;
        TypeStruct obj = OBJS.get(tname);
        if( obj != null )
            return TypeMemPtr.make(obj,match("?"));
        // Not a type; unwind the parse
        _lexer._position = old;
        return null;
    }

    /**
     * Parses a declStatement
     *
     * <pre>
     *     type name = expression ';'
     * </pre>
     * @return an expression {@link Node}
     */
    private Node parseDecl(Type t) {
        var name = requireId();
        var expr = match(";")
            // Assign a null value
            ? new ConstantNode(t.makeInit()).peephole()
            // Assign "= expr;"
            : require(require("=").parseExpression(), ";");
        if( expr._type instanceof TypeInteger && t instanceof TypeFloat )
            expr = new ToFloatNode(expr).peephole();
        if( !expr._type.isa(t) )
            throw error("Type " + expr._type.str() + " is not of declared type " + t.str());
        if( _scope.define(name,t,expr) == null )
            throw error("Redefining name '" + name + "'");
        return expr;
    }


    /**
     * Parse an expression of the form:
     *
     * <pre>
     *     expr : compareExpr
     * </pre>
     * @return an expression {@link Node}, never {@code null}
     */
    private Node parseExpression() { return parseComparison(); }

    /**
     * Parse an expression of the form:
     *
     * <pre>
     *     expr : additiveExpr op additiveExpr
     * </pre>
     * @return an comparator expression {@link Node}, never {@code null}
     */
    private Node parseComparison() {
        var lhs = parseAddition();
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
            lhs.setDef(idx,parseAddition());
            lhs = lhs.widen().peephole();
            if( negate )        // Extra negate for !=
                lhs = new NotNode(lhs).peephole();
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
            lhs = lhs.widen().peephole();
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
            lhs = lhs.widen().peephole();
        }
        return lhs;
    }

    /**
     * Parse a unary minus expression.
     *
     * <pre>
     *     unaryExpr : ('-') | '!') unaryExpr | postfixExpr | primaryExpr
     * </pre>
     * @return a unary expression {@link Node}, never {@code null}
     */
    private Node parseUnary() {
        if (match("-")) return new MinusNode(parseUnary()).widen().peephole();
        if (match("!")) return new   NotNode(parseUnary()).peephole();
        return parsePostfix(parsePrimary());
    }

    /**
     * Parse a primary expression:
     *
     * <pre>
     *     primaryExpr : integerLiteral | Identifier | true | false | null | new Identifier | '(' expression ')'
     * </pre>
     * @return a primary {@link Node}, never {@code null}
     */
    private Node parsePrimary() {
        if( _lexer.isNumber(_lexer.peek()) ) return parseLiteral();
        if( match("(") ) return require(parseExpression(), ")");
        if( matchx("true" ) ) return new ConstantNode(TypeInteger.constant(1)).peephole();
        if( matchx("false") ) return ZERO;
        if( matchx("null" ) ) return new ConstantNode(TypeMemPtr.NULLPTR).peephole();
        if( matchx("new") ) {
            String structName = requireId();
            TypeStruct obj = OBJS.get(structName);
            if( obj == null) throw errorSyntax("Unknown struct type '" + structName + "'");
            return newStruct(obj);
        }
        String name = _lexer.matchId();
        if( name == null) throw errorSyntax("an identifier or expression");
        Node n = _scope.lookup(name);
        if( n!=null ) return n;
        throw error("Undefined name '" + name + "'");
    }

    /**
     * Return a NewNode but also generate instructions to initialize it.
     */
    private Node newStruct(TypeStruct obj) {
        Node n = new NewNode(TypeMemPtr.make(obj), ctrl()).peephole().keep();
        int alias = START._aliasStarts.get(obj._name);
        for( Field field : obj._fields ) {
            memAlias(alias, new StoreNode(field._fname, alias, memAlias(alias), n, ZERO).peephole());
            alias++;
        }
        return n.unkeep();
    }

    // We set up memory aliases by inserting special vars in the scope these
    // variables are prefixed by $ so they cannot be referenced in Simple code.
    // Using vars has the benefit that all the existing machinery of scoping
    // and phis work as expected
    private Node memAlias(int alias         ) { return _scope.lookup(memName(alias)    ); }
    private Node memAlias(int alias, Node st) { return _scope.update(memName(alias), st); }
    public static String memName(int alias) { return ("$"+alias).intern(); }

    /**
     * Parse postfix expression. For now this is just a field
     * expression, but in future could be array index too.
     *
     * <pre>
     *     expr ('.' IDENTIFIER)* [ = expr ]
     * </pre>
     */
    private Node parsePostfix(Node expr) {
        if( !match(".") ) return expr;

        if( !(expr._type instanceof TypeMemPtr ptr) )
            throw error("Expected struct reference but got " + expr._type.str());

        String name = requireId().intern();
        int idx = ptr._obj==null ? -1 : ptr._obj.find(name);
        if( idx == -1 ) throw error("Accessing unknown field '" + name + "' from '" + ptr.str() + "'");
        int alias = START._aliasStarts.get(ptr._obj._name)+idx;

        if( match("=") ) {
            // Disambiguate "obj.fld==x" boolean test from "obj.fld=x" field assignment
            if( peek('=') ) _lexer._position--;
            else {
                Node val = parseExpression();
                memAlias(alias, new StoreNode(name, alias, memAlias(alias), expr, val).peephole());
                return expr;        // "obj.a = expr" returns the expression while updating memory
            }
        }

        Type declaredType = ptr._obj._fields[idx]._type;
        return parsePostfix(new LoadNode(name, alias, declaredType, memAlias(alias), expr).peephole());
    }

    /**
     * Parse integer literal
     *
     * <pre>
     *     integerLiteral: [1-9][0-9]* | [0]
     *     floatLiteral: [1-9][0-9]* | [0]
     * </pre>
     */
    private ConstantNode parseLiteral() {
        return (ConstantNode) new ConstantNode(_lexer.parseNumber()).peephole();
    }

    //////////////////////////////////
    // Utilities for lexical analysis

    // Return true and skip if "syntax" is next in the stream.
    private boolean match (String syntax) { return _lexer.match (syntax); }
    // Match must be "exact", not be followed by more id letters
    private boolean matchx(String syntax) { return _lexer.matchx(syntax); }
    // Return true and do NOT skip if 'ch' is next
    private boolean peek(char ch) { return _lexer.peek(ch); }
    private boolean peekIsId() { return _lexer.peekIsId(); }

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
