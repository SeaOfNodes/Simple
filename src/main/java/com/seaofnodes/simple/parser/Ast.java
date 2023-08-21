package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.common.CompilerException;
import com.seaofnodes.simple.lexer.ecstasy.Token;

import java.util.ArrayList;
import java.util.List;

public abstract class Ast {

    public static class Node {
    }

    public static class Expr extends Node {
        public final Token op;

        public Expr(Token op) {
            this.op = op;
        }
    }

    public static class Identifier extends Expr {
        public final String name;
        public Identifier(Token op) {
            super(op);
            name = op.getValueText();
        }
    }

    public static class Symbol extends Expr {
        public final String name;
        public Symbol(Token op) {
            super(op);
            name = op.getValueText();
        }
    }

    public static class Binary extends Expr {
        public final Expr expr1, expr2;

        public Binary(Token op, Expr expr1, Expr expr2) {
            super(op);
            this.expr1 = expr1;
            this.expr2 = expr2;
        }
    }

    public static class Unary extends Expr {
        public final Expr expr;

        public Unary(Token op, Expr expr) {
            super(op);
            this.expr = expr;
        }
    }

    public static class Constant extends Expr {
        public Constant(Token op) {
            super(op);
        }
    }

    public static class Statement extends Node {
    }

    public static class IfElse extends Statement {
        public final Expr expr;
        public final Statement ifStmt, elseStmt;

        public IfElse(Expr expr, Statement ifStmt, Statement elseStmt) {
            this.expr = expr;
            this.ifStmt = ifStmt;
            this.elseStmt = elseStmt;
        }
    }

    public static class While extends Statement {
        public Expr expr;
        public Statement stmt;

        public While() {
        }
        public void init(Expr expr, Statement stmt) {
            this.expr = expr;
            this.stmt = stmt;
        }
    }

    public static class Break extends Statement {
        public final Statement stmt;

        public Break(Statement stmt) {
            if (stmt == null)
            {
                throw new CompilerException("uneclosed break");
            }
            this.stmt = stmt;
        }
    }

    public static class Assign extends Statement {
        public final Identifier identifier;
        public final Expr expr;

        public Assign(Identifier identifier, Expr expr) {
            this.identifier = identifier;
            this.expr = expr;
        }
    }

    public static class Type extends Node {
        public final Token.Id typeId;

        public Type(Token.Id typeId) {
            this.typeId = typeId;
        }
    }

    public static class Declare extends Statement {
        public final Type type;
        public final Identifier identifier;

        public Declare(Type type, Identifier identifier) {
            this.type = type;
            this.identifier = identifier;
        }
    }

    public static class Block extends Statement {
        public final List<Statement> stmtList = new ArrayList<>();
    }
}
