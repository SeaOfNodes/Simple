/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * <p>
 * Contributor(s):
 * <p>
 * The Initial Developer of the Original Software is Dibyendu Majumdar.
 * <p>
 * This file is part of the Sea Of Nodes Simple Programming language
 * implementation. See https://github.com/SeaOfNodes/Simple
 * <p>
 * The contents of this file are subject to the terms of the
 * Apache License Version 2 (the "APL"). You may not use this
 * file except in compliance with the License. A copy of the
 * APL may be obtained from:
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.seaofnodes.simple.parser;

import java.text.NumberFormat;
import java.text.ParseException;

public class Lexer {

    private final char[] input;
    /**
     * Tracks current position in input buffer
     */
    private int position = 0;
    private char peek = ' ';

    private final NumberFormat numberFormat;

    public Lexer(String source) {
        input = source.toCharArray();
        numberFormat = NumberFormat.getInstance();
        numberFormat.setGroupingUsed(false);
    }

    private boolean isEOF() {
        return position >= input.length;
    }

    private char advance() {
        if (isEOF())
            return 0;   // Special value that causes parsing to terminate
        return input[position++];
    }

    private boolean readch() {
        peek = advance();
        return true;
    }

    private boolean readch(char c) {
        if (readch() && peek != c)
            return false;
        peek = ' ';
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
        while (isWhiteSpace(peek))
            readch();
    }

    /**
     * Parses number in format nnn[.nnn]
     * where n is a digit
     */
    private Token parseNumber() {
        assert Character.isDigit(peek);
        StringBuilder sb = new StringBuilder();
        sb.append(peek);
        while (readch() && Character.isDigit(peek))
            sb.append(peek);
        if (peek == '.') {
            sb.append(peek);
            while (readch() && Character.isDigit(peek))
                sb.append(peek);
        }
        String str = sb.toString();
        Number number = parseNumber(str);
        return Token.newNum(number, str);
    }

    private Number parseNumber(String str) {
        try {
            return numberFormat.parse(str);
        } catch (ParseException e) {
            throw new CompilerException("Failed to parse number " + str, e);
        }
    }

    private boolean isIdentifierStart(char ch) {
        return Character.isAlphabetic(ch) || ch == '_';
    }

    private boolean isIdentifierLetter(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private Token parseIdentifier() {
        assert isIdentifierStart(peek);
        StringBuilder sb = new StringBuilder();
        sb.append(peek);
        while (readch() && isIdentifierLetter(peek))
            sb.append(peek);
        return Token.newIdent(sb.toString());
    }

    public Token scan() {
        skipWhitespace();
        switch (peek) {
            case 0:
                return Token.EOF;
            case '&':
                return readch('&') ? Token.newPunct("&&") : Token.newPunct("&");
            case '|':
                return readch('|') ? Token.newPunct("||") : Token.newPunct("|");
            case '=':
                return readch('=') ? Token.newPunct("==") : Token.newPunct("=");
            case '<':
                return readch('=') ? Token.newPunct("<=") : Token.newPunct("<");
            case '>':
                return readch('=') ? Token.newPunct(">=") : Token.newPunct(">");
            default: {
                return scanOthers();
            }
        }
    }

    private Token scanOthers() {
        if (Character.isDigit(peek))
            return parseNumber();
        else if (isIdentifierLetter(peek))
            return parseIdentifier();
        Token token = Token.newPunct(Character.toString(peek));
        peek = ' ';
        return token;
    }
}
