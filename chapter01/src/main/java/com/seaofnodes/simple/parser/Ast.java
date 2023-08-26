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

import com.seaofnodes.simple.lexer.ecstasy.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A simple AST definition.
 */
public class Ast {

    private Ast() {
    }

    public interface Node {
    }

    public abstract static class Expr implements Node {
        public final Token op;

        protected Expr(Token op) {
            this.op = op;
        }
    }

    /**
     * A name in the language
     */
    public static class Identifier extends Expr {
        public final String name;

        public Identifier(Token op) {
            super(op);
            name = op.getValueText();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Reference to a name in the language
     */
    public static class Symbol extends Expr {
        public final String name;

        public Symbol(Token op) {
            super(op);
            name = op.getValueText();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class Binary extends Expr {
        public final Expr expr1;
        public final Expr expr2;

        public Binary(Token op, Expr expr1, Expr expr2) {
            super(op);
            this.expr1 = expr1;
            this.expr2 = expr2;
        }

        @Override
        public String toString() {
            return "(" + expr1 + op.getValueText() + expr2 + ")";
        }
    }

    public static class Unary extends Expr {
        public final Expr expr;

        public Unary(Token op, Expr expr) {
            super(op);
            this.expr = expr;
        }

        @Override
        public String toString() {
            return "(" + op.getValueText() + "(" + expr + "))";
        }
    }

    public static class Constant extends Expr {
        public Constant(Token op) {
            super(op);
        }

        @Override
        public String toString() {
            return op.getValueText();
        }
    }

    public abstract static class Statement implements Node {
    }

    public static class IfElse extends Statement {
        public final Expr expr;
        public final Statement ifStmt;
        public final Statement elseStmt;

        public IfElse(Expr expr, Statement ifStmt, Statement elseStmt) {
            this.expr = expr;
            this.ifStmt = ifStmt;
            this.elseStmt = elseStmt;
        }

        @Override
        public String toString() {
            if (elseStmt != null)
                return "if(" + expr + ") " + ifStmt + " else " + elseStmt;
            return "if(" + expr + ") " + ifStmt;
        }
    }

    public static class While extends Statement {
        public final Expr expr;
        public final Statement stmt;

        public While(Expr expr, Statement stmt) {
            this.expr = expr;
            this.stmt = stmt;
        }

        @Override
        public String toString() {
            return "while(" + expr.toString() + ") " + stmt;
        }
    }

    public static class Break extends Statement {
        @Override
        public String toString() {
            return "break;";
        }
    }

    public static class Assign extends Statement {
        public final Identifier identifier;
        public final Expr expr;

        public Assign(Identifier identifier, Expr expr) {
            this.identifier = identifier;
            this.expr = expr;
        }

        @Override
        public String toString() {
            return identifier.toString() + "=" + expr.toString() + ";";
        }
    }

    public static class Type implements Node {
        public final Token.Id typeId;

        public Type(Token.Id typeId) {
            this.typeId = typeId;
        }

        @Override
        public String toString() {
            return typeId.TEXT;
        }
    }

    public static class Declare extends Statement {
        public final Type type;
        public final Identifier identifier;

        public Declare(Type type, Identifier identifier) {
            this.type = type;
            this.identifier = identifier;
        }

        @Override
        public String toString() {
            return type.toString() + " " + identifier.toString() + ";";
        }
    }

    public static class Block extends Statement {
        public final List<Statement> stmtList = new ArrayList<>();

        @Override
        public String toString() {
            return "{" +
                    stmtList.stream().map(Object::toString).collect(Collectors.joining()) + "}";
        }
    }
}
