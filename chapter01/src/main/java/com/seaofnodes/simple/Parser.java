package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;

import java.text.NumberFormat;
import java.text.ParseException;

/**
 * The Parser converts a Simple source program to the Sea of Nodes intermediate representation
 * directly in one pass. There is no intermediate Abstract Syntax Tree structure.
 *
 * This is a simple recursive descent parser. All lexical analysis is done here as well.
 */
public class Parser {

    /**
     * Current token from lexer
     */
    private Token _curTok;

    private final NodeIDGenerator _idGenerator;

    private StartNode _startNode;

    public Parser(NodeIDGenerator idGenerator) {
        _idGenerator = idGenerator;
    }

    public StartNode parse(String source) {
        Lexer lexer = new Lexer(source);
        nextToken(lexer);
        return parseProgram(lexer);
    }

    private StartNode parseProgram(Lexer lexer) {
        _startNode = new StartNode(_idGenerator);
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
        return new ReturnNode(_idGenerator, _startNode, returnExpr);
    }

    /**
     * Parse an expression of the form:
     *
     * <pre>
     *     expr : primaryExpr
     * </pre>
     */
    private Node parseExpression(Lexer lexer) {
        return parsePrimary(lexer);
    }

    /**
     * Parse a primary expression:
     *
     * <pre>
     *     primaryExpr : nestedExpr | integerLiteral
     * </pre>
     */
    private Node parsePrimary(Lexer lexer) {
        switch (_curTok._kind) {
            case PUNCT -> {
                /* Nested expression */
                return parseNestedExpr(lexer);
            }
            case NUM -> {
                return parseIntegerLiteral(lexer);
            }
            default -> {
                error(_curTok, "syntax error, expected nested expr or integer literal");
                return null;
            }
        }
    }

    /**
     * Parse nested expression:
     *
     * <pre>
     *     nestedExpr : ( expr )
     * </pre>
     */
    private Node parseNestedExpr(Lexer lexer) {
        matchPunctuation(lexer, "(");
        nextToken(lexer);
        var node = parsePrimary(lexer);
        matchPunctuation(lexer, ")");
        return node;
    }

    /**
     * Parse integer literal
     *
     * <pre>
     *     integerLiteral: [1-9][0-9]*
     * </pre>
     */
    private ConstantNode parseIntegerLiteral(Lexer lexer) {
        var constantNode = new ConstantNode(_idGenerator, _curTok._num.longValue(), _startNode);
        nextToken(lexer);
        return constantNode;
    }

    //////////////////////////////////
    // Utilities for lexical analysis

    private void nextToken(Lexer lexer) {
        _curTok = lexer.next();
    }

    private boolean isToken(Token token, String value) {
        return token._str.equals(value);
    }

    private void error(Token t, String errorMessage) {
        throw new RuntimeException(errorMessage + ": " + t.toString());
    }

    private void matchPunctuation(Lexer lexer, String value) {
        if (_curTok._kind == Token.Kind.PUNCT && isToken(_curTok, value)) {
            nextToken(lexer);
        } else {
            error(_curTok, "syntax error, expected " + value + " got " + _curTok._str);
        }
    }

    private void matchIdentifier(Lexer lexer, String identifier) {
        if (_curTok._kind == Token.Kind.IDENT && isToken(_curTok, identifier)) {
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
         * String representation of a token - always
         * populated
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

        public String toString() {
            return _str;
        }
    }

    static class Lexer {

        /**
         * Input buffer
         */
        private final char[] _input;
        /**
         * Tracks current position in input buffer
         */
        private int _position = 0;
        /**
         * Current character
         */
        private char _cur = ' ';

        private final NumberFormat numberFormat;

        public Lexer(String source) {
            _input = source.toCharArray();
            numberFormat = NumberFormat.getInstance();
            numberFormat.setGroupingUsed(false);
        }

        private boolean isEOF() {
            return _position >= _input.length;
        }

        private char advance() {
            if (isEOF())
                return 0;   // Special value that causes parsing to terminate
            return _input[_position++];
        }

        private boolean nextToken() {
            _cur = advance();
            return true;
        }

        /**
         * Note that EOF is not whitespace!
         */
        private boolean isWhiteSpace(char c) {
            return c == ' ' ||
                    c == '\t' ||
                    c == '\n' ||
                    c == '\r';
        }

        private void skipWhitespace() {
            while (isWhiteSpace(_cur))
                nextToken();
        }

        /**
         * Parses number in format nnn[.nnn]
         * where n is a digit
         */
        private Token parseNumber() {
            assert Character.isDigit(_cur);
            StringBuilder sb = new StringBuilder();
            sb.append(_cur);
            while (nextToken() && Character.isDigit(_cur))
                sb.append(_cur);
            if (_cur == '.') {
                sb.append(_cur);
                while (nextToken() && Character.isDigit(_cur))
                    sb.append(_cur);
            }
            String str = sb.toString();
            Number number = parseNumber(str);
            return Token.newNum(number, str);
        }

        private Number parseNumber(String str) {
            try {
                return numberFormat.parse(str);
            } catch (ParseException e) {
                throw new RuntimeException("Failed to parse number " + str, e);
            }
        }

        private boolean isIdentifierStart(char ch) {
            return Character.isAlphabetic(ch) || ch == '_';
        }

        private boolean isIdentifierLetter(char ch) {
            return Character.isLetterOrDigit(ch) || ch == '_';
        }

        private Token parseIdentifier() {
            assert isIdentifierStart(_cur);
            StringBuilder sb = new StringBuilder();
            sb.append(_cur);
            while (nextToken() && isIdentifierLetter(_cur))
                sb.append(_cur);
            return Token.newIdent(sb.toString());
        }

        /**
         * Gets the next token
         */
        public Token next() {
            skipWhitespace();
            if (_cur == 0)
                return Token.EOF;
            if (Character.isDigit(_cur))
                return parseNumber();
            if (isIdentifierLetter(_cur))
                return parseIdentifier();
            Token token = Token.newPunct(Character.toString(_cur));
            _cur = ' ';
            return token;
        }
    }
}
