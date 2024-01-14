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
    private final HashSet<String> KEYWORDS = new HashSet<>(){{
            add("break");
            add("continue");
            add("else");
            add("false");
            add("if");
            add("int");
            add("return");
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

    public Parser(String source, TypeInteger arg) {
        Node.reset();
        _lexer = new Lexer(source);
        _scope = new ScopeNode();
        _continueScope = _breakScope = null;
        START = new StartNode(new Type[]{ Type.CONTROL, arg });
        STOP = new StopNode();
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
        _scope.define(ScopeNode.CTRL, new ProjNode(START, 0, ScopeNode.CTRL).peephole());
        _scope.define(ScopeNode.ARG0, new ProjNode(START, 1, ScopeNode.ARG0).peephole());
        parseBlock();
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
        if (matchx("return")  ) return parseReturn();
        else if (matchx("int")) return parseDecl();
        else if (match ("{"  )) return require(parseBlock(),"}");
        else if (matchx("if" )) return parseIf();
        else if (matchx("while")) return parseWhile();
        else if (matchx("break")) return parseBreak();
        else if (matchx("continue")) return parseContinue();
        else if (matchx("#showGraph")) return require(showGraph(),";");
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
        IfNode ifNode = (IfNode)new IfNode(ctrl(), pred).keep().peephole();
        // Setup projection nodes
        Node ifT = new ProjNode(ifNode, 0, "True" ).peephole();
        ifNode.unkeep();
        Node ifF = new ProjNode(ifNode, 1, "False").peephole();

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
        ctrl(ifT);              // set ctrl token to ifTrue projection
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
        ctrl(new ConstantNode(Type.XCONTROL).peephole()); // Kill current scope
        // Prune nested lexical scopes that have depth > than the loop head
        // We use _breakScope as a proxy for the loop head scope to obtain the depth
        while( cur._scopes.size() > _breakScope._scopes.size() )
            cur.pop();
        // If this is a continue then first time the target is null
        // So we just use the pruned current scope as the base for the
        // continue
        if (toScope == null)
            return cur;
        // toScope is either the break scope, or a scope that was created here
        assert toScope._scopes.size() <= _breakScope._scopes.size();
        toScope.mergeScopes(cur);
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
        var pred = require(parseExpression(), ")");
        // IfNode takes current control and predicate
        IfNode ifNode = (IfNode)new IfNode(ctrl(), pred).<IfNode>keep().peephole();
        // Setup projection nodes
        Node ifT = new ProjNode(ifNode, 0, "True" ).peephole();
        ifNode.unkeep();
        Node ifF = new ProjNode(ifNode, 1, "False").peephole();
        // In if true branch, the ifT proj node becomes the ctrl
        // But first clone the scope and set it as current
        int ndefs = _scope.nIns();
        ScopeNode fScope = _scope.dup(); // Duplicate current scope
        _xScopes.push(fScope); // For graph visualization we need all scopes

        // Parse the true side
        ctrl(ifT);              // set ctrl token to ifTrue projection
        parseStatement();       // Parse true-side
        ScopeNode tScope = _scope;
        
        // Parse the false side
        _scope = fScope;        // Restore scope, then parse else block if any
        ctrl(ifF);              // Ctrl token is now set to ifFalse projection
        if (matchx("else")) {
            parseStatement();
            fScope = _scope;
        }

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
        Node ret = STOP.addReturn(new ReturnNode(ctrl(), expr).peephole());
        ctrl(new ConstantNode(Type.XCONTROL).peephole()); // Kill control
        return ret;
    }

    /**
     * Dumps out the node graph
     * @return {@code null}
     */
    private Node showGraph() {
        System.out.println(new GraphVisualizer().generateDotOutput(this));
        return null;
    }

    /**
     * Parses an expression statement
     *
     * <pre>
     *     name '=' expression ';'
     * </pre>
     * @return an expression {@link Node}, never {@code null}
     */
    private Node parseExpressionStatement() {
        var name = requireId();
        require("=");
        var expr = require(parseExpression(), ";");
        if( _scope.update(name, expr)==null )
            throw error("Undefined name '" + name + "'");
        return expr;
    }

    /**
     * Parses a declStatement
     *
     * <pre>
     *     'int' name = expression ';'
     * </pre>
     * @return an expression {@link Node}, never {@code null}
     */
    private Node parseDecl() {
        // Type is 'int' for now
        var name = requireId();
        require("=");
        var expr = require(parseExpression(), ";");
        if( _scope.define(name,expr) == null )
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
        if (match("==")) return new BoolNode.EQ(lhs, parseComparison()).peephole();
        if (match("!=")) return new NotNode(new BoolNode.EQ(lhs, parseComparison()).peephole()).peephole();
        if (match("<" )) return new BoolNode.LT(lhs, parseComparison()).peephole();
        if (match("<=")) return new BoolNode.LE(lhs, parseComparison()).peephole();
        if (match(">" )) return new BoolNode.LT(parseComparison(), lhs).peephole();
        if (match(">=")) return new BoolNode.LE(parseComparison(), lhs).peephole();
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
        var lhs = parseMultiplication();
        if (match("+")) return new AddNode(lhs, parseAddition()).peephole();
        if (match("-")) return new SubNode(lhs, parseAddition()).peephole();
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
        if (match("*")) return new MulNode(lhs, parseMultiplication()).peephole();
        if (match("/")) return new DivNode(lhs, parseMultiplication()).peephole();
        return lhs;
    }

    /**
     * Parse a unary minus expression.
     *
     * <pre>
     *     unaryExpr : ('-') unaryExpr | primaryExpr
     * </pre>
     * @return a unary expression {@link Node}, never {@code null}
     */
    private Node parseUnary() {
        if (match("-")) return new MinusNode(parseUnary()).peephole();
        return parsePrimary();
    }

    /**
     * Parse a primary expression:
     *
     * <pre>
     *     primaryExpr : integerLiteral | Identifier | true | false | '(' expression ')'
     * </pre>
     * @return a primary {@link Node}, never {@code null}
     */
    private Node parsePrimary() {
        if( _lexer.isNumber() ) return parseIntegerLiteral();
        if( match("(") ) return require(parseExpression(), ")");
        if( matchx("true" ) ) return new ConstantNode(TypeInteger.constant(1)).peephole();
        if( matchx("false") ) return new ConstantNode(TypeInteger.constant(0)).peephole();
        String name = _lexer.matchId();
        if( name == null) throw errorSyntax("an identifier or expression");
        Node n = _scope.lookup(name);
        if( n!=null ) return n;
        throw error("Undefined name '" + name + "'");
    }

    /**
     * Parse integer literal
     *
     * <pre>
     *     integerLiteral: [1-9][0-9]* | [0]
     * </pre>
     */
    private ConstantNode parseIntegerLiteral() {
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

    // Require and return an identifier
    private String requireId() {
        String id = _lexer.matchId();
        if (id != null && !KEYWORDS.contains(id) ) return id;
        throw error("Expected an identifier, found '"+id+"'");
    }

    // Require an exact match
    private void require(String syntax) { require(null, syntax); }
    private <N extends Node> N require(N n, String syntax) {
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
            while (isWhiteSpace()) _position++;
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
        
        // Return an identifier or null
        String matchId() {
            skipWhiteSpace();
            return isIdStart(peek()) ? parseId() : null;
        }

        // Used for errors
        String getAnyNextToken() {
            if (isEOF()) return "";
            if (isIdStart(peek())) return parseId();
            if (isPunctuation(peek())) return parsePunctuation();
            return String.valueOf(peek());
        }

        boolean isNumber() {return isNumber(peek());}
        boolean isNumber(char ch) {return Character.isDigit(ch);}

        private Type parseNumber() {
            int start = _position;
            while (isNumber(nextChar())) ;
            String snum = new String(_input, start, --_position - start);
            if (snum.length() > 1 && snum.charAt(0) == '0')
                throw error("Syntax error: integer values cannot start with '0'");
            return TypeInteger.constant(Long.parseLong(snum));
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
