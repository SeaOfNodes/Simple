package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;

/**
 * The Parser converts a Simple source program to the Sea of Nodes intermediate representation
 * directly in one pass. There is no intermediate Abstract Syntax Tree structure.
 * <p>
 * This is a simple recursive descent parser. All lexical analysis is done here as well.
 */
public class Parser {

    /**
     * Current token from lexer
     */
    private Token _curTok;

    private StartNode _startNode;

    public StartNode parse(String source) {
        Lexer lexer = new Lexer(source);
        nextToken(lexer);
        return parseProgram(lexer);
    }

    private StartNode parseProgram(Lexer lexer) {
        _startNode = new StartNode();
        parseReturnStatement(lexer);
        return _startNode;
    }

    /**
     * Parses return statement.
     *
     * <pre>
     *     return expr ;
     * </pre>
     */
    private ReturnNode parseReturnStatement(Lexer lexer) {
        matchIdentifier(lexer, "return");
        var returnExpr = parseExpression(lexer);
        matchPunctuation(lexer, ";");
        return new ReturnNode(_startNode, returnExpr);
    }

    /**
     * Parse an expression of the form:
     *
     * <pre>
     *     expr : additiveExpr
     * </pre>
     */
    private Node parseExpression(Lexer lexer) {
        return parseAddition(lexer);
    }

    /**
     * Parse a unary minus expression.
     *
     * <pre>
     *     unaryExpr : ('-') unaryExpr | primaryExpr
     * </pre>
     */
    private Node parseUnary(Lexer lexer) {
        if (nextTokenIf(lexer, "-")) {
            return new UnaryMinusNode(parseUnary(lexer));
        } else {
            return parsePrimary(lexer);
        }
    }

    /**
     * Parse an additive expression
     *
     * <pre>
     *     additiveExpr : multiplicativeExpr (('+' | '-') multiplicativeExpr)*
     * </pre>
     */
    private Node parseAddition(Lexer lexer) {
        var x = parseMultiplication(lexer);
        var tok = _curTok;
        while (nextTokenIf(lexer, "-", "+")) {
            x = match(tok, "+")
                    ? new AddNode(x, parseMultiplication(lexer))
                    : new SubtractNode(x, parseMultiplication(lexer));
            x = x.peephole(_startNode);
            tok = _curTok;
        }
        return x;
    }


    /**
     * Parse an multiplicativeExpr expression
     *
     * <pre>
     *     multiplicativeExpr : unaryExpr (('*' | '/') unaryExpr)*
     * </pre>
     */
    private Node parseMultiplication(Lexer lexer) {
        var x = parseUnary(lexer);
        var tok = _curTok;
        while (nextTokenIf(lexer, "*", "/")) {
            x = match(tok, "*")
                    ? new MultiplyNode(x, parseUnary(lexer))
                    : new DivideNode(x, parseUnary(lexer));
            tok = _curTok;
        }
        return x;
    }

    /**
     * Parse a primary expression:
     *
     * <pre>
     *     primaryExpr : integerLiteral
     * </pre>
     */
    private Node parsePrimary(Lexer lexer) {
        switch (_curTok._kind) {
            case NUM -> {
                return parseIntegerLiteral(lexer);
            }
            default -> {
                error(_curTok, "syntax error, expected integer literal");
                return null;
            }
        }
    }

    /**
     * Parse integer literal
     *
     * <pre>
     *     integerLiteral: [1-9][0-9]* | [0]
     * </pre>
     */
    private ConstantNode parseIntegerLiteral(Lexer lexer) {
        var constantNode = new ConstantNode(_curTok._num.longValue(), _startNode);
        nextToken(lexer);
        return constantNode;
    }

    //////////////////////////////////
    // Utilities for lexical analysis

    private void nextToken(Lexer lexer) {
        _curTok = lexer.next();
    }

    private boolean nextTokenIf(Lexer lexer, String... look) {
        for (String t : look)
            if (match(_curTok, t)) {
                nextToken(lexer);
                return true;
            }
        return false;
    }

    private boolean match(Token token, String value) {
        return token._str.equals(value);
    }

    private void error(Token t, String errorMessage) {
        throw new RuntimeException(errorMessage + ": " + t.toString());
    }

    private void matchPunctuation(Lexer lexer, String value) {
        if (_curTok._kind == Token.Kind.PUNCT && match(_curTok, value)) {
            nextToken(lexer);
        } else {
            error(_curTok, "syntax error, expected " + value + " got " + _curTok._str);
        }
    }

    private void matchIdentifier(Lexer lexer, String identifier) {
        if (_curTok._kind == Token.Kind.IDENT && match(_curTok, identifier)) {
            nextToken(lexer);
        } else {
            error(_curTok, "syntax error, expected " + identifier);
        }
    }

    ////////////////////////////////////
    // Lexer components

    static class Token {

        enum Kind {
            IDENT,
            NUM,
            PUNCT,
            EOZ     // Special kind to signal end of file
        }

        public final Kind _kind;
        /**
         * String representation of a token - always populated
         */
        public final String _str;
        /**
         * The parsed number value, only populated for Kind.NUM
         */
        public final Number _num;

        public Token(Kind kind, String str, Number num) {
            _kind = kind;
            _str = str;
            _num = num;
        }

        public static Token newIdent(String str) {
            return new Token(Kind.IDENT, str.intern(), null);
        }

        public static Token newNum(Number num, String str) {
            return new Token(Kind.NUM, str, num);
        }

        public static Token newPunct(String str) {
            return new Token(Kind.PUNCT, str.intern(), null);
        }

        /**
         * Special token that indicates that source has been exhausted
         */
        public static Token EOF = new Token(Kind.EOZ, "", null);

        @Override
        public String toString() {
            return _str;
        }
    }

    static class Lexer {

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

        public Lexer(byte[] buf) {
            _input = buf;
        }

        // True if at EOF
        private boolean isEOF() {
            return _position >= _input.length;
        }

        private char nextChar() {
            char ch = peek();
            _position++;
            return ch;
        }

        // Peek next character, or report EOF
        private char peek() {
            return isEOF() ? Character.MAX_VALUE   // Special value that causes parsing to terminate
                    : (char) _input[_position];
        }

        // True if a white space
        private boolean isWhiteSpace(char c) {
            return c <= ' '; // Includes all the use space, tab, newline, CR
        }

        /**
         * Return the next non-white-space character
         */
        private char skipWhiteSpace() {
            char c = nextChar();
            while (isWhiteSpace(c))
                c = nextChar();
            return c;
        }

        private boolean isDigit(char ch) {
            return ('0' <= ch && ch <= '9');
        }

        private Token parseNumber() {
            int start = _position - 1;
            while (Character.isDigit(nextChar())) ;
            String snum = new String(_input, start, --_position - start);
            if (snum.length() > 1 && snum.charAt(0) == '0')
                throw error("syntax error: integer values cannot start with '0'");
            return Token.newNum(Integer.parseInt(snum), snum);
        }

        // First letter of an identifier 
        private boolean isIdentifierStart(char ch) {
            return Character.isAlphabetic(ch) || ch == '_';
        }

        // All characters of an identifier, e.g. "_x123"
        private boolean isIdentifierLetter(char ch) {
            return Character.isLetterOrDigit(ch) || ch == '_';
        }

        private Token parseIdentifier() {
            int start = _position - 1;
            while (isIdentifierLetter(nextChar())) ;
            return Token.newIdent(new String(_input, start, --_position - start));
        }

        // 
        private boolean isPunctuation(char ch) {
            return "=;[]<>()+-/*".indexOf(ch) != -1;
        }

        private Token parsePuncuation() {
            int start = _position - 1;
            if (isPunctuation(nextChar())) ;
            return Token.newPunct(new String(_input, start, --_position - start));
        }

        /**
         * Gets the next Token
         */
        public Token next() {
            char ch = skipWhiteSpace();
            if (ch == Character.MAX_VALUE)
                return Token.EOF;
            if (isDigit(ch))
                return parseNumber();
            if (isIdentifierStart(ch))
                return parseIdentifier();
            if (isPunctuation(ch))
                return parsePuncuation();

            throw error("syntax error: unexpected input '" + ch + "'");
        }

        private RuntimeException error(String msg) {
            throw new RuntimeException(msg);
        }
    }

}
