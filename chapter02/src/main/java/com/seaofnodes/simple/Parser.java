package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

/**
 * The Parser converts a Simple source program to the Sea of Nodes intermediate
 * representation directly in one pass. There is no intermediate Abstract
 * Syntax Tree structure.
 * <p>
 * This is a simple recursive descent parser. All lexical analysis is done here as well.
 */
public class Parser {

    private final Lexer _lexer;

    /**
     * A Global Static, unique to each compilation.  This is a public, so we
     * can make constants everywhere without having to thread the StartNode
     * through the entire parser and optimizer.
     * <p>
     * To make the compiler multithreaded, this field will have to move into a TLS.
     */    
    public static StartNode START;

    public Parser( String source ) {
        _lexer = new Lexer(source);
        START = new StartNode();
    }
    
    public ReturnNode parse() {
        return parseReturn();
    }

    /**
     * Parses return statement.
     *
     * <pre>
     *     return expr ;
     * </pre>
     */
    private ReturnNode parseReturn() {
        require("return");
        var returnExpr = parseExpression();
        require(";");
        return new ReturnNode(START, returnExpr);
    }

    /**
     * Parse an expression of the form:
     *
     * <pre>
     *     expr : additiveExpr
     * </pre>
     */
    private Node parseExpression() {
        return parseAddition();
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
        if( match("+") ) return new AddNode(lhs, parseAddition()).peephole();
        if( match("-") ) return new SubNode(lhs, parseAddition()).peephole();
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
        if( match("*") ) return new MulNode(lhs, parseMultiplication()).peephole();
        if( match("/") ) return new DivNode(lhs, parseMultiplication()).peephole();
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
        if( match("-") ) return new MinusNode(parseUnary()).peephole();
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
        if( _lexer.isNumber() )
            return parseIntegerLiteral();
        throw error("syntax error, expected integer literal");
    }

    /**
     * Parse integer literal
     *
     * <pre>
     *     integerLiteral: [1-9][0-9]* | [0]
     * </pre>
     */
    private ConstantNode parseIntegerLiteral() {
        return (ConstantNode)new ConstantNode(_lexer.parseNumber()).peephole();
    }

    static RuntimeException error(String errorMessage) {
        return new RuntimeException(errorMessage);
    }

    //////////////////////////////////
    // Utilities for lexical analysis

    // Return true and skip if "syntax" is next in the stream.
    private boolean match(String syntax) {
        return _lexer.match(syntax);
    }

    // Require an exact match
    private void require(String syntax) {
        if( match(syntax) ) return;
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
            while (isWhiteSpace())
                _position++;
        }


        // Return true, if we find "syntax" after skipping white space; also
        // then advance the cursor past syntax.
        // Return false otherwise, and do not advance the cursor.
        boolean match( String syntax ) {
            skipWhiteSpace();
            int len = syntax.length();
            if( _position + len > _input.length )
                return false;
            for( int i=0; i<len; i++ )
                if( (char)_input[_position+i] != syntax.charAt(i) )
                    return false;
            _position += len;
            return true;
        }

        // Used for errors
        String getAnyNextToken() {
            if( isEOF() ) return "";
            if( isIdentifierStart(peek()) )
                return parseIdentifier();
            if( isPunctuation(peek()) )
                return parsePunctuation();
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
