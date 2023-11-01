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

    private final Lexer _lexer;

    /**
     * Stack of lexical scopes, each scope is a symbol table that binds
     * variable names to Nodes.  The top of this stack represents current scope.
     */
    public ScopeNode _scope;

    public Parser(String source, TypeInteger arg) {
        _lexer = new Lexer(source);
        _scope = new ScopeNode();
        Node.reset();
        START = new StartNode(new Type[]{ Type.CONTROL, arg });
        START.peephole();
    }

    public Parser(String source) {
        this(source, TypeInteger.BOT);
    }

    String src() { return new String( _lexer._input ); }



    public ReturnNode parse() {
        _scope.push();
        try {
            _scope.define("$ctrl", new ProjNode(START, 0, "$ctrl").peephole());
            _scope.define("arg"  , new ProjNode(START, 1, "arg"  ).peephole());
            return (ReturnNode) parseBlock();
        }
        finally {
            _scope.pop();
        }
    }

    /**
     * Parses a block
     *
     * <pre>
     *     '{' statements '}'
     * </pre>
     */
    private Node parseBlock() {
        // Enter a new scope
        _scope.push();
        Node n = null;
        while (!match("}")) {
            if (_lexer.isEOF()) throw errorSyntax("}");

            Node n0 = parseStatement();
            if (n0 != null) n = n0; // Allow null returns from eg showGraph
        }
        // Exit scope
        _scope.pop();
        return n;
    }

    /**
     * Parses a statement
     *
     * <pre>
     *     returnStatement | declStatement | blockStatement | expressionStatement
     * </pre>
     */
    private Node parseStatement() {
        if (match("return")) return parseReturn();
        else if (match("int")) return parseDecl();
        else if (match("{")) return parseBlock();
        else if (match("#showGraph")) return showGraph();
        else return parseExpressionStatement();
    }

    /**
     * Parses a return statement; "return" already parsed.
     * The $ctrl edge is killed.
     *
     * <pre>
     *     'return' expr ;
     * </pre>
     */
    private Node parseReturn() {
        var expr = require(parseExpression(), ";");
        Node ret = new ReturnNode(_scope.lookup("$ctrl"), expr);
        _scope.update("$ctrl",null);
        return ret;
    }

    /**
     * Dumps out the node graph
     */
    private Node showGraph() {
        require(";");
        System.out.println(new GraphVisualizer().generateDotOutput(this));
        return null;
    }

    /**
     * Parses an expression statement
     *
     * <pre>
     *     name '=' expression ';'
     * </pre>
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
     *     expr : additiveExpr
     * </pre>
     */
    private Node parseExpression() { return parseComparison(); }

    private Node parseComparison() {
        var lhs = parseAddition();
        if (match("==")) return new BoolNode.EQNode(lhs, parseComparison()).peephole();
        if (match("!=")) return new BoolNode.NENode(lhs, parseComparison()).peephole();
        if (match("<" )) return new BoolNode.LTNode(lhs, parseComparison()).peephole();
        if (match("<=")) return new BoolNode.LENode(lhs, parseComparison()).peephole();
        if (match(">" )) return new BoolNode.GTNode(lhs, parseComparison()).peephole();
        if (match(">=")) return new BoolNode.GENode(lhs, parseComparison()).peephole();
        return lhs;
    }

    /**
     * Parse an additive expression
     *
     * <pre>
     *     additiveExpr : multiplicativeExpr (('+' | '-') multiplicativeExpr)*
     * </pre>
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
     */
    private Node parseUnary() {
        if (match("-")) return new MinusNode(parseUnary()).peephole();
        return parsePrimary();
    }

    /**
     * Parse a primary expression:
     *
     * <pre>
     *     primaryExpr : integerLiteral | Identifier | '(' expression ')'
     * </pre>
     */
    private Node parsePrimary() {
        if (_lexer.isNumber()) return parseIntegerLiteral();
        if (match("(")) return require(parseExpression(), ")");
        String name = requireId();
        Node id = _scope.lookup(name);
        if( id==null )
            throw error("Undefined name '" + name + "'");
        return id.peephole();
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
    private boolean match(String syntax) { return _lexer.match(syntax); }

    // Require and return an identifier
    private String requireId() {
        String id = _lexer.matchId();
        if (id != null) return id;
        throw error("identifier");
    }

    // Require an exact match
    private void require(String syntax) { require(null, syntax); }
    private Node require(Node n, String syntax) {
        if (match(syntax)) return n;
        throw errorSyntax(syntax);
    }

    RuntimeException errorSyntax(String syntax) {
        return error("Syntax error, expected " + syntax + ": " + _lexer.getAnyNextToken());
    }

    static RuntimeException error(String errorMessage) {
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
                if ((char) _input[_position + i] != syntax.charAt(i)) return false;
            _position += len;
            return true;
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
            if (isPunctuation(nextChar())) ;
            return new String(_input, start, --_position - start);
        }
    }

}
