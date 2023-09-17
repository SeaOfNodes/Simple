package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;

import java.text.NumberFormat;
import java.text.ParseException;

/**
 * The Parser converts a Simple source program to the Sea of Nodes intermediate representation
 * directly in one pass. There is no intermediate Abstract Syntax Tree structure. The parser also
 * does the lexical analysis of the input source.
 */
public class Parser {

    private Token currentToken;
    private final NodeIDGenerator _idGenerator;
    private StartNode _startNode;

    public Parser() {
        _idGenerator = new NodeIDGenerator();
    }

    public Node parse(String source) {
        Lexer lexer = new Lexer(source);
        _startNode = new StartNode(_idGenerator, new Node[0]);
        nextToken(lexer);
        return parseProgram(lexer);
    }

    private Node parseProgram(Lexer lexer) {
        parseReturnStatement(lexer);
        return _startNode;
    }

    private void nextToken(Lexer lexer) {
        currentToken = lexer.next();
    }

    private boolean isToken(Token token, String value) {
        return token._str.equals(value);
    }

    private void error(Token t, String errorMessage) {
        throw new RuntimeException(errorMessage + ": " + t.toString());
    }

    private void matchPunctuation(Lexer lexer, String value) {
        if (currentToken._kind == Token.Kind.PUNCT && isToken(currentToken, value)) {
            nextToken(lexer);
        } else {
            error(currentToken, "syntax error, expected " + value + " got " + currentToken._str);
        }
    }

    private void matchIdentifier(Lexer lexer, String identifier) {
        if (currentToken._kind == Token.Kind.IDENT && isToken(currentToken, identifier)) {
            nextToken(lexer);
        } else {
            error(currentToken, "syntax error, expected " + identifier);
        }
    }

    private Node parsePrimary(Lexer lexer) {
        switch (currentToken._kind) {
            case PUNCT -> {
                /* Nested expression */
                return parseNestedExpr(lexer);
            }
            case NUM -> {
                return parseNumberConstant(lexer);
            }
            default -> {
                error(currentToken, "syntax error, expected nested expr or integer literal");
                return null;
            }
        }
    }

    private ConstantNode parseNumberConstant(Lexer lexer) {
        var constantNode = new ConstantNode(_idGenerator, currentToken._num.longValue(), _startNode);
        nextToken(lexer);
        return constantNode;
    }

    private Node parseNestedExpr(Lexer lexer) {
        matchPunctuation(lexer, "(");
        nextToken(lexer);
        var node = parsePrimary(lexer);
        matchPunctuation(lexer, ")");
        return node;
    }

    private Node parseReturnStatement(Lexer lexer) {
        matchIdentifier(lexer, "return");
        var returnExpr = parsePrimary(lexer);
        matchPunctuation(lexer, ";");
        return new ReturnNode(_idGenerator, _startNode, returnExpr);
    }

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

        private final char[] _input;
        /**
         * Tracks current position in input buffer
         */
        private int _position = 0;
        private char _curCh = ' ';

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
            _curCh = advance();
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
            while (isWhiteSpace(_curCh))
                nextToken();
        }

        /**
         * Parses number in format nnn[.nnn]
         * where n is a digit
         */
        private Token parseNumber() {
            assert Character.isDigit(_curCh);
            StringBuilder sb = new StringBuilder();
            sb.append(_curCh);
            while (nextToken() && Character.isDigit(_curCh))
                sb.append(_curCh);
            if (_curCh == '.') {
                sb.append(_curCh);
                while (nextToken() && Character.isDigit(_curCh))
                    sb.append(_curCh);
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
            assert isIdentifierStart(_curCh);
            StringBuilder sb = new StringBuilder();
            sb.append(_curCh);
            while (nextToken() && isIdentifierLetter(_curCh))
                sb.append(_curCh);
            return Token.newIdent(sb.toString());
        }

        /**
         * Gets the next token
         */
        public Token next() {
            skipWhitespace();
            if (_curCh == 0)
                return Token.EOF;
            if (Character.isDigit(_curCh))
                return parseNumber();
            if (isIdentifierLetter(_curCh))
                return parseIdentifier();
            Token token = Token.newPunct(Character.toString(_curCh));
            _curCh = ' ';
            return token;
        }
    }
}
