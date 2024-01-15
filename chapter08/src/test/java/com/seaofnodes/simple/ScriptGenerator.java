package com.seaofnodes.simple;

import com.seaofnodes.simple.node.ScopeNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

public class ScriptGenerator {

    @SuppressWarnings("unchecked")
    private static HashSet<String> getKeywords() {
        try {
            var field = Parser.class.getDeclaredField("KEYWORDS");
            field.setAccessible(true);
            return (HashSet<String>) field.get(new Parser(""));
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final int INDENTATION = 4;
    private static final String VAR_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789";
    private static final HashSet<String> KEYWORDS = getKeywords();

    private static final int FLAG_STOP = 0x01;
    private static final int FLAG_IF_WITHOUT_ELSE = 0x02;

    private final Random random;
    private int indentation = 0;
    private final StringBuilder sb;
    private int loopDepth = 0;
    private int currScopeStart = 0;
    private int depth = 0;
    private int exprDepth = 3;
    private final ArrayList<String> variables = new ArrayList<>();
    private final boolean generateValid;
    private boolean maybeInvalid = false;

    public ScriptGenerator(Random random, StringBuilder sb, boolean generateValid) {
        this.random = random;
        this.sb = sb;
        this.generateValid = generateValid;
    }

    private int randLog(int max) {
        max--;
        int n = 1 << max;
        var num = random.nextInt(n);
        for(int i=max-1; i>=0; i--) {
            if ((num & (1 << i))!=0) return max-i-1;
        }
        return max;
    }

    private StringBuilder printIndentation() {
        return sb.repeat(' ', indentation);
    }

    public boolean genProgram() {
        this.maybeInvalid = false;
        variables.add(ScopeNode.ARG0);
        depth = random.nextInt(9) + 1;
        currScopeStart = variables.size();
        if ((genStatements() & FLAG_STOP) != 0)
            genReturn();
        return !this.maybeInvalid;
    }

    public int genStatements() {
        var num = random.nextInt(10);
        for (int i=0; i<num; i++) {
            printIndentation();
            var stop = genStatement();
            sb.append("\n");
            if ((stop & FLAG_STOP) != 0) return stop;
        }
        return 0;
    }

    public int genStatement() {
        return switch (random.nextInt(10)) {
            case 1 -> genBlock();
            case 2 -> genIf();
            case 3 -> genWhile();
            case 4, 5 -> genDecl();
            case 6, 7, 8 -> genAssignment();
            default -> genExit();
        };
    }

    public int genStatementBlock() {
        indentation += INDENTATION;
        int res;
        if (depth == 0) {
            res = random.nextBoolean() ? genAssignment() : genExit();
        } else {
            depth--;
            res = switch (random.nextInt(10)) {
                case 1, 2, 3, 4, 5 -> {
                    depth++;
                    indentation -= INDENTATION;
                    var ret = genBlock();
                    indentation += INDENTATION;
                    depth--;
                    yield ret;
                }
                case 6 -> genIf();
                case 7 -> genWhile();
                case 8 -> genAssignment();
                default -> genExit();
            };
            depth++;
        }
        indentation -= INDENTATION;
        return res;
    }

    public int genExit() {
        if (loopDepth == 0 && generateValid) return genReturn();
        return switch (random.nextInt(7)) {
            case 0,1,2 -> {
                if (loopDepth == 0) maybeInvalid = true;
                sb.append("continue;");
                yield FLAG_STOP;
            }
            case 3,4,5 -> {
                if (loopDepth == 0) maybeInvalid = true;
                sb.append("break;");
                yield FLAG_STOP;
            }
            default -> genReturn();
        };
    }

    public int genBlock() {
        if (depth == 0) {
            sb.append("{}");
            return 0;
        }
        depth--;
        int oldCSS = currScopeStart;
        currScopeStart = variables.size();
        sb.append("{\n");
        indentation += INDENTATION;
        var stop = genStatements();
        indentation -= INDENTATION;
        printIndentation().append("}");
        while (variables.size() > currScopeStart)
            variables.removeLast();
        currScopeStart = oldCSS;
        depth++;
        return stop & ~FLAG_IF_WITHOUT_ELSE;
    }

    public int genIf() {
        sb.append("if(");
        genExpression();
        sb.append(") ");
        var stop = genStatementBlock();
        if ((stop & FLAG_IF_WITHOUT_ELSE) == 0 && random.nextInt(10) > 3) {
            sb.append("\n");
            printIndentation().append("else ");
            stop &= genStatementBlock();
        } else {
            stop = FLAG_IF_WITHOUT_ELSE;
        }
        return stop;
    }

    public int genWhile() {
        sb.append("while(");
        genExpression();
        sb.append(") ");
        loopDepth++;
        var stop = genStatementBlock();
        loopDepth--;
        return stop & FLAG_IF_WITHOUT_ELSE;
    }

    private StringBuilder getRandomName() {
        int len = random.nextInt(10) + 1;
        StringBuilder sb = new StringBuilder(len);
        sb.append(VAR_CHARS.charAt(random.nextInt(VAR_CHARS.length()-10)));
        for (int i=1; i<len; i++)
            sb.append(VAR_CHARS.charAt(random.nextInt(VAR_CHARS.length())));
        return sb;
    }

    private String getVarName() {
        if (currScopeStart > 0 && random.nextInt(10) > 7) {
            var v = variables.get(random.nextInt(currScopeStart));
            if (variables.lastIndexOf(v) < currScopeStart) return v;
            if (!generateValid) {
                maybeInvalid = true;
                return v;
            }
        }
        StringBuilder sb = getRandomName();
        var v = sb.toString();
        if (!generateValid && (KEYWORDS.contains(v) || variables.lastIndexOf(v) >= currScopeStart)) {
            maybeInvalid = true;
            return v;
        }
        while (KEYWORDS.contains(v) || variables.lastIndexOf(v) >= currScopeStart) {
            sb.append(VAR_CHARS.charAt(random.nextInt(VAR_CHARS.length())));
            v = sb.toString();
        }
        return v;
    }

    public int genDecl() {
        String name = getVarName();
        sb.append("int ").append(name).append("=");
        genExpression();
        sb.append(";");
        variables.add(name);
        return 0;
    }

    public int genAssignment() {
        if (variables.isEmpty()) return genDecl();
        genVariable();
        sb.append("=");
        genExpression();
        sb.append(";");
        return 0;
    }

    public int genReturn() {
        sb.append("return ");
        genExpression();
        sb.append(";");
        return FLAG_STOP;
    }

    private static final String[] EXPRESSIONS = {"==", "!=", "<", "<=", ">", ">=", "+", "-", "*", "/"};
    private static final String[] UNARY = {"-"};

    public void genExpression() {
        var num = randLog(10);
        while(num-->0) {
            genUnary();
            sb.append(EXPRESSIONS[random.nextInt(EXPRESSIONS.length)]);
        }
        genUnary();
    }

    public void genUnary() {
        var num = randLog(6);
        while(num-->0) {
            sb.append(UNARY[random.nextInt(UNARY.length)]);
        }
        genPrimary();
    }

    public void genPrimary() {
        switch (random.nextInt(10)) {
            case 0:
                sb.append("true");
                break;
            case 1:
                sb.append("false");
                break;
            case 2:
                if (exprDepth == 0) {
                    if (random.nextBoolean()) {
                        genNumber();
                    } else {
                        genVariable();
                    }
                } else {
                    sb.append("(");
                    exprDepth--;
                    genExpression();
                    exprDepth++;
                    sb.append(")");
                }
                break;
            case 3, 4, 5, 6:
                genNumber();
                break;
            default:
                genVariable();
        }
    }

    public void genNumber() {
        sb.append(random.nextInt(100));
    }

    public void genVariable() {
        if (!generateValid && random.nextInt(100)>97) {
            sb.append(getRandomName());
            maybeInvalid = true;
            return;
        }
        if (variables.isEmpty()) {
            genNumber();
        } else {
            sb.append(variables.get(random.nextInt(variables.size())));
        }
    }

}
