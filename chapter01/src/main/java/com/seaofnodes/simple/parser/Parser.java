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

public class Parser {
    private Token currentToken;

    /**
     * Parse and return a top level Ast
     * At present this is an Ast.Block
     *
     * @see Ast.Block
     */
    public Ast parse(Lexer lexer) {
        nextToken(lexer);
        return parseProgram(lexer);
    }

    private void nextToken(Lexer lexer) {
        currentToken = lexer.scan();
    }

    private void error(Token t, String errorMessage) {
        throw new CompilerException(errorMessage + ": " + t.toString());
    }

    private void matchKind(Lexer lexer, Token.Kind kind) {
        if (currentToken.kind == kind) {
            nextToken(lexer);
        } else {
            error(currentToken, "syntax error, expected " + kind);
        }
    }

    private void matchPunctuation(Lexer lexer, String value) {
        if (currentToken.kind == Token.Kind.PUNCT && isToken(currentToken, value)) {
            nextToken(lexer);
        } else {
            error(currentToken, "syntax error, expected " + value + " got " + currentToken.str);
        }
    }

    private void matchIdentifier(Lexer lexer, String identifier) {
        if (currentToken.kind == Token.Kind.IDENT && isToken(currentToken, identifier)) {
            nextToken(lexer);
        } else {
            error(currentToken, "syntax error, expected " + identifier);
        }
    }

    private boolean isToken(Token token, String value) {
        return token.str.equals(value);
    }

    private Ast.Statement parseProgram(Lexer lexer) {
        var block = new Ast.Block();
        parseDeclarations(lexer, block);
        parseStatements(lexer, block);
        return block;
    }

    private boolean isTypeName(Token tok) {
        return tok.kind == Token.Kind.IDENT &&
                (isToken(tok, "int") ||
                        isToken(tok, "char") ||
                        isToken(tok, "float"));
    }

    private void parseDeclarations(Lexer lexer, Ast.Block block) {
        while (isTypeName(currentToken)) {
            var type = new Ast.Type(currentToken.str);
            nextToken(lexer);
            var tok = currentToken;
            matchKind(lexer, Token.Kind.IDENT);
            matchPunctuation(lexer, ";");
            var identifier = new Ast.Identifier(tok);
            block.stmtList.add(new Ast.Declare(type, identifier));
        }
    }

    private boolean isEOF(Token tok) {
        return tok.kind == Token.Kind.EOZ;
    }

    private boolean isPunct(Token tok, String str) {
        return tok.kind == Token.Kind.PUNCT && isToken(tok, str);
    }

    private void parseStatements(Lexer lexer, Ast.Block block) {
        while (!isEOF(currentToken) && !isPunct(currentToken, "}")) {
            block.stmtList.add(parseStatement(lexer));
        }
    }

    private Ast.Statement parseStatement(Lexer lexer) {
        Ast.Expr x;
        Ast.Statement s1;
        Ast.Statement s2;

        switch (currentToken.str) {
            case "if" -> {
                matchIdentifier(lexer, "if");
                matchPunctuation(lexer, "(");
                x = parseBool(lexer);
                matchPunctuation(lexer, ")");
                s1 = parseStatement(lexer);
                if (!isToken(currentToken, "else")) {
                    return new Ast.IfElse(x, s1, null);
                }
                s2 = parseStatement(lexer);
                return new Ast.IfElse(x, s1, s2);
            }
            case "while" -> {
                matchIdentifier(lexer, "while");
                matchPunctuation(lexer, "(");
                x = parseBool(lexer);
                matchPunctuation(lexer, ")");
                s1 = parseStatement(lexer);
                return new Ast.While(x, s1);
            }
            case "break" -> {
                matchIdentifier(lexer, "break");
                matchPunctuation(lexer, ";");
                return new Ast.Break();
            }
            case "{" -> {
                return parseBlock(lexer);
            }
            default -> {
                return parseAssign(lexer);
            }
        }
    }

    private Ast.Statement parseBlock(Lexer lexer) {
        matchPunctuation(lexer, "{");
        var block = new Ast.Block();
        parseDeclarations(lexer, block);
        parseStatements(lexer, block);
        matchPunctuation(lexer, "}");
        return block;
    }

    private Ast.Statement parseAssign(Lexer lexer) {
        Token tok = currentToken;
        matchKind(lexer, Token.Kind.IDENT);
        Ast.Identifier identifier = new Ast.Identifier(tok);
        matchPunctuation(lexer, "=");
        var s = new Ast.Assign(identifier, parseBool(lexer));
        matchPunctuation(lexer, ";");
        return s;
    }

    private Ast.Expr parseBool(Lexer lexer) {
        var x = parseAnd(lexer);
        while (isToken(currentToken, "||")) {
            var tok = currentToken;
            nextToken(lexer);
            x = new Ast.Binary(tok, x, parseAnd(lexer));
        }
        return x;
    }

    private Ast.Expr parseAnd(Lexer lexer) {
        var x = parseRelational(lexer);
        while (isToken(currentToken, "&&")) {
            var tok = currentToken;
            nextToken(lexer);
            x = new Ast.Binary(tok, x, parseRelational(lexer));
        }
        return x;
    }

    private Ast.Expr parseRelational(Lexer lexer) {
        var x = parseAddition(lexer);
        while (isToken(currentToken, "==") ||
                isToken(currentToken, "!=") ||
                isToken(currentToken, "<=") ||
                isToken(currentToken, "<") ||
                isToken(currentToken, ">") ||
                isToken(currentToken, ">=")) {
            var tok = currentToken;
            nextToken(lexer);
            x = new Ast.Binary(tok, x, parseAddition(lexer));
        }
        return x;
    }

    private Ast.Expr parseAddition(Lexer lexer) {
        var x = parseMultiplication(lexer);
        while (isToken(currentToken, "-") ||
                isToken(currentToken, "+")) {
            var tok = currentToken;
            nextToken(lexer);
            x = new Ast.Binary(tok, x, parseMultiplication(lexer));
        }
        return x;
    }

    private Ast.Expr parseMultiplication(Lexer lexer) {
        var x = parseUnary(lexer);
        while (isToken(currentToken, "*") ||
                isToken(currentToken, "/")) {
            var tok = currentToken;
            nextToken(lexer);
            x = new Ast.Binary(tok, x, parseUnary(lexer));
        }
        return x;
    }

    private Ast.Expr parseUnary(Lexer lexer) {
        if (isToken(currentToken, "-") ||
                isToken(currentToken, "!")) {
            var tok = currentToken;
            nextToken(lexer);
            return new Ast.Unary(tok, parseUnary(lexer));
        } else {
            return parsePrimary(lexer);
        }
    }

    private Ast.Expr parsePrimary(Lexer lexer) {
        switch (currentToken.kind) {
            case PUNCT -> {
                /* Nested expression */
                matchPunctuation(lexer, "(");
                nextToken(lexer);
                var x = parseBool(lexer);
                matchPunctuation(lexer, ")");
                return x;
            }
            case NUM -> {
                var x = new Ast.Constant(currentToken);
                nextToken(lexer);
                return x;
            }
            case IDENT -> {
                var x = new Ast.Symbol(currentToken);
                nextToken(lexer);
                return x;
            }
            default -> {
                error(currentToken, "syntax error, expected nested expr, integer value or variable");
                return null;
            }
        }
    }
}
