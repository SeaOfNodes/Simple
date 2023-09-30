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

    private ReturnNode _ret;

    private final Lexer _lexer;

    /**
     * Stack of lexical scopes
     */
    public Stack<Map<String, Node>> _scopes;

    public Parser(String source) {
        _lexer = new Lexer(source);
        _scopes = new Stack<>();
        Node.reset();
        START = new StartNode();
    }

    static RuntimeException error(String errorMessage) {
        return new RuntimeException(errorMessage);
    }

    private void enterScope() {_scopes.push(new HashMap<>());}

    private void exitScope() {_scopes.pop();}

    // Create a new name in top-most scope
    private void define(String name, Node n) {
        // new name
        _scopes.lastElement().put(name, n);
    }

    // If the name is present in any scope, then redefine
    private void update(String name, Node n) {
        for (int i = _scopes.size() - 1; i >= 0; i--) {
            if (_scopes.get(i).get(name) != null) {
                _scopes.get(i).put(name, n);
                return;
            }
        }
        throw error("Undefined name '" + name + "'");
    }

    // Lookup a name in all scopes
    private Node lookup(String name) {
        for (int i = _scopes.size() - 1; i >= 0; i--) {
            var n = _scopes.get(i).get(name);
            if (n != null) return n;
        }
        throw error("Undefined name '" + name + "'");
    }

    public ReturnNode parse() {
        enterScope();
        parseStatements();
        exitScope();
        return _ret;
    }

    private void parseStatements() {
        _lexer.skipWhiteSpace();
        while (_lexer.peek() != '}' && !_lexer.isEOF()) {
            parseStatement();
            _lexer.skipWhiteSpace();
        }
    }

    /**
     * Parses a statement
     *
     * <pre>
     *     returnStatement | declStatement | blockStatement | expressionStatement
     * </pre>
     */
    private void parseStatement() {
        if (_lexer.match("return")) parseReturn();
        else if (_lexer.match("int")) parseDecl();
        else if (_lexer.match("{")) parseBlock();
        else parseExpressionStatement();
    }

    /**
     * Parses an expression statement
     *
     * <pre>
     *     name '=' expression ';'
     * </pre>
     */
    private void parseExpressionStatement() {
        var name = requireIdentifier();
        require("=");
        var expr = parseExpression();
        require(";");
        update(name, expr);
    }

    /**
     * Parses a declStatement
     *
     * <pre>
     *     'int' name = expression ';'
     * </pre>
     */
    private void parseDecl() {
        // Type is 'int' for now
        var name = requireIdentifier();
        require("=");
        var expr = parseExpression();
        define(name, expr);
        require(";");
    }

    /**
     * Parses a blockStatement
     *
     * <pre>
     *     '{' statements '}'
     * </pre>
     */
    private void parseBlock() {
        enterScope();
        parseStatements();
        require("}");
        exitScope();
    }

    /**
     * Parses returnStatement.
     *
     * <pre>
     *     'return' expr ;
     * </pre>
     */
    private void parseReturn() {
        if (_ret != null) throw error("Multiple return statements");
        var expr = parseExpression();
        require(";");
        _ret = new ReturnNode(START, expr);
    }

    /**
     * Parse an expression of the form:
     *
     * <pre>
     *     expr : additiveExpr
     * </pre>
     */
    private Node parseExpression() {return parseAddition();}

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
     *     primaryExpr : integerLiteral
     * </pre>
     */
    private Node parsePrimary() {
        if (_lexer.isNumber()) return parseIntegerLiteral();
        else return lookup(requireIdentifier());
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
    private boolean match(String syntax) {
        return _lexer.match(syntax);
    }

    private Optional<String> matchIdentifier() {return _lexer.matchIdentifier();}

    private String requireIdentifier() {
        return matchIdentifier().orElseThrow(() -> error("Expected identifier"));
    }

    // Require an exact match
    private void require(String syntax) {
        _lexer.skipWhiteSpace();
        if (match(syntax)) return;
        throw error("Syntax error, expected " + syntax + ": " + _lexer.getAnyNextToken());
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

        Optional<String> matchIdentifier() {
            skipWhiteSpace();
            if (isIdentifierStart(peek())) return Optional.of(parseIdentifier());
            return Optional.empty();
        }

        // Used for errors
        String getAnyNextToken() {
            if (isEOF()) return "";
            if (isIdentifierStart(peek())) return parseIdentifier();
            if (isPunctuation(peek())) return parsePunctuation();
            return String.valueOf(peek());
        }

        boolean isNumber() {
            return isNumber(peek());
        }

        boolean isNumber(char ch) {
            return Character.isDigit(ch);
        }

        private Type parseNumber() {
            int start = _position;
            while (isNumber(nextChar())) ;
            String snum = new String(_input, start, --_position - start);
            if (snum.length() > 1 && snum.charAt(0) == '0')
                throw error("Syntax error: integer values cannot start with '0'");
            return new TypeInteger(Long.parseLong(snum));
        }

        // First letter of an identifier 
        private boolean isIdentifierStart(char ch) {
            return Character.isAlphabetic(ch) || ch == '_';
        }

        // All characters of an identifier, e.g. "_x123"
        private boolean isIdentifierLetter(char ch) {
            return Character.isLetterOrDigit(ch) || ch == '_';
        }

        private String parseIdentifier() {
            int start = _position;
            while (isIdentifierLetter(nextChar())) ;
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
