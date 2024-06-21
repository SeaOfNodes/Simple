package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;

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

    // The Lexer.  Thin wrapper over a byte[] buffer with a cursor.
    private final Lexer _lexer;


    public Parser(String source) {
        _lexer = new Lexer(source);
        Node.reset();
        START = new StartNode();
    }

    public ReturnNode parse() {
        require("return");
        return parseReturn();
    }

    /**
     * Parses a return statement; "return" already parsed.
     *
     * <pre>
     *     'return' expr ;
     * </pre>
     * @return an expression {@link Node}, never {@code null}
     */
    private ReturnNode parseReturn() {
        var expr = require(parseExpression(), ";");
        return new ReturnNode(START, expr);
    }

    /**
     * Parse an expression of the form:
     *
     * <pre>
     *     expr : primaryExpr
     * </pre>
     * @return an expression {@link Node}, never {@code null}
     */
    private Node parseExpression() {
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
        _lexer.skipWhiteSpace();
        if (_lexer.isNumber())
            return parseIntegerLiteral();
        throw error("Syntax error, expected integer literal");
    }

    /**
     * Parse integer literal
     *
     * <pre>
     *     integerLiteral: [1-9][0-9]* | [0]
     * </pre>
     */
    private ConstantNode parseIntegerLiteral() {
        return new ConstantNode(_lexer.parseNumber());
    }

    //////////////////////////////////
    // Utilities for lexical analysis

    // Return true and skip if "syntax" is next in the stream.
    private boolean match(String syntax) { return _lexer.match(syntax); }

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

        // Used for errors
        String getAnyNextToken() {
            if (isEOF()) return "";
            if (isIdStart(peek())) return parseId();
            if (isPunctuation(peek())) return parsePunctuation();
            return String.valueOf(peek());
        }

        boolean isNumber() {return isNumber(peek());}
        boolean isNumber(char ch) {return Character.isDigit(ch);}

        private long parseNumber() {
            int start = _position;
            while (isNumber(nextChar())) ;
            String snum = new String(_input, start, --_position - start);
            if (snum.length() > 1 && snum.charAt(0) == '0')
                throw error("Syntax error: integer values cannot start with '0'");
            return Long.parseLong(snum);
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
