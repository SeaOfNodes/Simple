/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Contributor(s):
 *
 * The Initial Developer of the Original Software is Dibyendu Majumdar.
 *
 * This file is part of the Sea Of Nodes Simple Programming language
 * implementation. See https://github.com/SeaOfNodes/Simple
 *
 * The contents of this file are subject to the terms of the
 * Apache License Version 2 (the "APL"). You may not use this
 * file except in compliance with the License. A copy of the
 * APL may be obtained from:
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.lexer.ecstasy.CompilerException;
import com.seaofnodes.simple.lexer.ecstasy.Lexer;
import com.seaofnodes.simple.lexer.ecstasy.Token;
import com.seaofnodes.simple.lexer.ecstasy.Severity;

public class Parser {
    final Lexer lexer;
    Token look;

    public Parser(Lexer lexer) {
        this.lexer = lexer;
    }

    private Token nextOrEof() {
        if (lexer.hasNext())
            return lexer.next();
        return new Token(look.getStartPosition(), look.getEndPosition(), Token.Id.EOF);
    }

    private void nextToken() {
        look = nextOrEof();
        // skip comments
        while (look.isComment()) {
            look = nextOrEof();
        }
        if (look.isContextSensitive() &&
                look.getId() == Token.Id.IDENTIFIER) {
            look = look.convertToKeyword();
        }
    }

    private void error(Token t, String errorMessage) {
        lexer.log(Severity.ERROR, errorMessage, t.getStartPosition(), t.getEndPosition());
        throw new CompilerException(errorMessage + ": " + t.toDebugString());
    }

    private void match(Token.Id tag) {
        if (look.getId() == tag) {
            nextToken();
        } else {
            error(look, "syntax error, expected " + tag);
        }
    }

    public Ast.Node parse() {
        nextToken();
        return parseProgram();
    }

    private Ast.Statement parseProgram() {
        var block = new Ast.Block();
        parseDeclarations(block);
        parseStatements(block);
        return block;
    }

    private boolean isTypeName(Token tok) {
        return switch (tok.getId()) {
            case TYPE_INT, TYPE_CHAR, TYPE_FLOAT -> true;
            default -> false;
        };
    }

    private void parseDeclarations(Ast.Block block) {
        while (isTypeName(look)) {
            var type = new Ast.Type(look.getId());
            nextToken();
            var tok = look;
            match(Token.Id.IDENTIFIER);
            match(Token.Id.SEMICOLON);
            var identifier = new Ast.Identifier(tok);
            block.stmtList.add(new Ast.Declare(type, identifier));
        }
    }

    private void parseStatements(Ast.Block block) {
        while (look.getId() != Token.Id.EOF &&
                look.getId() != Token.Id.R_CURLY) {
            block.stmtList.add(parseStatement());
        }
    }

    private Ast.Statement parseStatement() {
        Ast.Expr x;
        Ast.Statement s1;
        Ast.Statement s2;

        switch (look.getId()) {
            case IF -> {
                match(Token.Id.IF);
                match(Token.Id.L_PAREN);
                x = parseBool();
                match(Token.Id.R_PAREN);
                s1 = parseStatement();
                if (look.getId() != Token.Id.ELSE) {
                    return new Ast.IfElse(x, s1, null);
                }
                s2 = parseStatement();
                return new Ast.IfElse(x, s1, s2);
            }
            case WHILE -> {
                match(Token.Id.WHILE);
                match(Token.Id.L_PAREN);
                x = parseBool();
                match(Token.Id.R_PAREN);
                s1 = parseStatement();
                return new Ast.While(x, s1);
            }
            case BREAK -> {
                match(Token.Id.BREAK);
                match(Token.Id.SEMICOLON);
                return new Ast.Break();
            }
            case L_CURLY -> {
                return parseBlock();
            }
            default -> {
                return parseAssign();
            }
        }
    }

    private Ast.Statement parseBlock() {
        match(Token.Id.L_CURLY);
        var block = new Ast.Block();
        parseDeclarations(block);
        parseStatements(block);
        match(Token.Id.R_CURLY);
        return block;
    }

    private Ast.Statement parseAssign() {
        Token tok = look;
        match(Token.Id.IDENTIFIER);
        Ast.Identifier identifier = new Ast.Identifier(tok);
        match(Token.Id.ASN);
        var s = new Ast.Assign(identifier, parseBool());
        match(Token.Id.SEMICOLON);
        return s;
    }

    private Ast.Expr parseBool() {
        var x = parseAnd();
        while (look.getId() == Token.Id.COND_OR) {
            var tok = look;
            nextToken();
            x = new Ast.Binary(tok, x, parseAnd());
        }
        return x;
    }

    private Ast.Expr parseAnd() {
        var x = parseRelational();
        while (look.getId() == Token.Id.COND_AND) {
            var tok = look;
            nextToken();
            x = new Ast.Binary(tok, x, parseRelational());
        }
        return x;
    }

    private Ast.Expr parseRelational() {
        var x = parseAddition();
        while (look.getId() == Token.Id.COMP_EQ ||
                look.getId() == Token.Id.COMP_NEQ ||
                look.getId() == Token.Id.COMP_LT ||
                look.getId() == Token.Id.COMP_GT ||
                look.getId() == Token.Id.COMP_GTEQ ||
                look.getId() == Token.Id.COMP_LTEQ) {
            var tok = look;
            nextToken();
            x = new Ast.Binary(tok, x, parseAddition());
        }
        return x;
    }

    private Ast.Expr parseAddition() {
        var x = parseMultiplication();
        while (look.getId() == Token.Id.ADD ||
                look.getId() == Token.Id.SUB) {
            var tok = look;
            nextToken();
            x = new Ast.Binary(tok, x, parseMultiplication());
        }
        return x;
    }

    private Ast.Expr parseMultiplication() {
        var x = parseUnary();
        while (look.getId() == Token.Id.MUL ||
                look.getId() == Token.Id.DIV) {
            var tok = look;
            nextToken();
            x = new Ast.Binary(tok, x, parseUnary());
        }
        return x;
    }

    private Ast.Expr parseUnary() {
        if (look.getId() == Token.Id.SUB ||
                look.getId() == Token.Id.NOT) {
            var tok = look;
            nextToken();
            return new Ast.Unary(tok, parseUnary());
        } else {
            return parsePrimary();
        }
    }

    private Ast.Expr parsePrimary() {
        switch (look.getId()) {
            case L_PAREN -> {
                nextToken();
                var x = parseBool();
                match(Token.Id.R_PAREN);
                return x;
            }
            case LIT_INT -> {
                var x = new Ast.Constant(look);
                nextToken();
                return x;
            }
            case IDENTIFIER -> {
                var x = new Ast.Symbol(look);
                nextToken();
                return x;
            }
            default -> {
                error(look, "syntax error, expected nested expr, integer value or variable");
                return null;
            }
        }
    }
}
