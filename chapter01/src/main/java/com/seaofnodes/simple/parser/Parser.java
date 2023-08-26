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
    private Token currentToken;

    /**
     * Parse and return a top level Ast
     * At present this is an Ast.Block
     * @see Ast.Block
     */
    public Ast parse(Lexer lexer) {
        nextToken(lexer);
        return parseProgram(lexer);
    }

    private Token nextOrEof(Lexer lexer) {
        if (lexer.hasNext())
            return lexer.next();
        return new Token(currentToken.getStartPosition(), currentToken.getEndPosition(), Token.Id.EOF);
    }

    private void nextToken(Lexer lexer) {
        currentToken = nextOrEof(lexer);
        // skip comments
        while (currentToken.isComment()) {
            currentToken = nextOrEof(lexer);
        }
        if (currentToken.isContextSensitive() &&
                currentToken.getId() == Token.Id.IDENTIFIER) {
            currentToken = currentToken.convertToKeyword();
        }
    }

    private void error(Lexer lexer, Token t, String errorMessage) {
        lexer.log(Severity.ERROR, errorMessage, t.getStartPosition(), t.getEndPosition());
        throw new CompilerException(errorMessage + ": " + t.toDebugString());
    }

    private void match(Lexer lexer, Token.Id tag) {
        if (currentToken.getId() == tag) {
            nextToken(lexer);
        } else {
            error(lexer, currentToken, "syntax error, expected " + tag);
        }
    }


    private Ast.Statement parseProgram(Lexer lexer) {
        var block = new Ast.Block();
        parseDeclarations(lexer, block);
        parseStatements(lexer, block);
        return block;
    }

    private boolean isTypeName(Token tok) {
        return switch (tok.getId()) {
            case TYPE_INT, TYPE_CHAR, TYPE_FLOAT -> true;
            default -> false;
        };
    }

    private void parseDeclarations(Lexer lexer, Ast.Block block) {
        while (isTypeName(currentToken)) {
            var type = new Ast.Type(currentToken.getId());
            nextToken(lexer);
            var tok = currentToken;
            match(lexer, Token.Id.IDENTIFIER);
            match(lexer, Token.Id.SEMICOLON);
            var identifier = new Ast.Identifier(tok);
            block.stmtList.add(new Ast.Declare(type, identifier));
        }
    }

    private void parseStatements(Lexer lexer, Ast.Block block) {
        while (currentToken.getId() != Token.Id.EOF &&
                currentToken.getId() != Token.Id.R_CURLY) {
            block.stmtList.add(parseStatement(lexer));
        }
    }

    private Ast.Statement parseStatement(Lexer lexer) {
        Ast.Expr x;
        Ast.Statement s1;
        Ast.Statement s2;

        switch (currentToken.getId()) {
            case IF -> {
                match(lexer, Token.Id.IF);
                match(lexer, Token.Id.L_PAREN);
                x = parseBool(lexer);
                match(lexer, Token.Id.R_PAREN);
                s1 = parseStatement(lexer);
                if (currentToken.getId() != Token.Id.ELSE) {
                    return new Ast.IfElse(x, s1, null);
                }
                s2 = parseStatement(lexer);
                return new Ast.IfElse(x, s1, s2);
            }
            case WHILE -> {
                match(lexer, Token.Id.WHILE);
                match(lexer, Token.Id.L_PAREN);
                x = parseBool(lexer);
                match(lexer, Token.Id.R_PAREN);
                s1 = parseStatement(lexer);
                return new Ast.While(x, s1);
            }
            case BREAK -> {
                match(lexer, Token.Id.BREAK);
                match(lexer, Token.Id.SEMICOLON);
                return new Ast.Break();
            }
            case L_CURLY -> {
                return parseBlock(lexer);
            }
            default -> {
                return parseAssign(lexer);
            }
        }
    }

    private Ast.Statement parseBlock(Lexer lexer) {
        match(lexer, Token.Id.L_CURLY);
        var block = new Ast.Block();
        parseDeclarations(lexer, block);
        parseStatements(lexer, block);
        match(lexer, Token.Id.R_CURLY);
        return block;
    }

    private Ast.Statement parseAssign(Lexer lexer) {
        Token tok = currentToken;
        match(lexer, Token.Id.IDENTIFIER);
        Ast.Identifier identifier = new Ast.Identifier(tok);
        match(lexer, Token.Id.ASN);
        var s = new Ast.Assign(identifier, parseBool(lexer));
        match(lexer, Token.Id.SEMICOLON);
        return s;
    }

    private Ast.Expr parseBool(Lexer lexer) {
        var x = parseAnd(lexer);
        while (currentToken.getId() == Token.Id.COND_OR) {
            var tok = currentToken;
            nextToken(lexer);
            x = new Ast.Binary(tok, x, parseAnd(lexer));
        }
        return x;
    }

    private Ast.Expr parseAnd(Lexer lexer) {
        var x = parseRelational(lexer);
        while (currentToken.getId() == Token.Id.COND_AND) {
            var tok = currentToken;
            nextToken(lexer);
            x = new Ast.Binary(tok, x, parseRelational(lexer));
        }
        return x;
    }

    private Ast.Expr parseRelational(Lexer lexer) {
        var x = parseAddition(lexer);
        while (currentToken.getId() == Token.Id.COMP_EQ ||
                currentToken.getId() == Token.Id.COMP_NEQ ||
                currentToken.getId() == Token.Id.COMP_LT ||
                currentToken.getId() == Token.Id.COMP_GT ||
                currentToken.getId() == Token.Id.COMP_GTEQ ||
                currentToken.getId() == Token.Id.COMP_LTEQ) {
            var tok = currentToken;
            nextToken(lexer);
            x = new Ast.Binary(tok, x, parseAddition(lexer));
        }
        return x;
    }

    private Ast.Expr parseAddition(Lexer lexer) {
        var x = parseMultiplication(lexer);
        while (currentToken.getId() == Token.Id.ADD ||
                currentToken.getId() == Token.Id.SUB) {
            var tok = currentToken;
            nextToken(lexer);
            x = new Ast.Binary(tok, x, parseMultiplication(lexer));
        }
        return x;
    }

    private Ast.Expr parseMultiplication(Lexer lexer) {
        var x = parseUnary(lexer);
        while (currentToken.getId() == Token.Id.MUL ||
                currentToken.getId() == Token.Id.DIV) {
            var tok = currentToken;
            nextToken(lexer);
            x = new Ast.Binary(tok, x, parseUnary(lexer));
        }
        return x;
    }

    private Ast.Expr parseUnary(Lexer lexer) {
        if (currentToken.getId() == Token.Id.SUB ||
                currentToken.getId() == Token.Id.NOT) {
            var tok = currentToken;
            nextToken(lexer);
            return new Ast.Unary(tok, parseUnary(lexer));
        } else {
            return parsePrimary(lexer);
        }
    }

    private Ast.Expr parsePrimary(Lexer lexer) {
        switch (currentToken.getId()) {
            case L_PAREN -> {
                nextToken(lexer);
                var x = parseBool(lexer);
                match(lexer, Token.Id.R_PAREN);
                return x;
            }
            case LIT_INT -> {
                var x = new Ast.Constant(currentToken);
                nextToken(lexer);
                return x;
            }
            case IDENTIFIER -> {
                var x = new Ast.Symbol(currentToken);
                nextToken(lexer);
                return x;
            }
            default -> {
                error(lexer, currentToken, "syntax error, expected nested expr, integer value or variable");
                return null;
            }
        }
    }
}
