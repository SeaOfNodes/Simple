package com.seaofnodes.simple.fuzzer;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.ScopeNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

/**
 * Generate a pseudo random script.
 * This generator will generate the same script for two random number generators initialized with the same seed.
 * To generate valid code it implements the parser but instead of parsing it emits the statements and expressions.
 * It is guaranteed to terminate.
 */
public class ScriptGenerator {

    /**
     * Number of spaces per indentation
     */
    private static final int INDENTATION = 4;
    /**
     * Valid characters for identifiers. The last 10 are not valid for the first character.
     */
    private static final String VAR_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789";
    /**
     * List of keywords not valid for identifiers.
     */
    private static final HashSet<String> KEYWORDS;

    static {
        try {
            KEYWORDS = FuzzerUtils.getFieldValue(new Parser(""), "KEYWORDS");
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Flag for a statement that it does not pass on control flow to the next statement.
     */
    private static final int FLAG_STOP = 0x01;
    /**
     * Flag for a statement that it contains a final if statement without an else branch.
     */
    private static final int FLAG_IF_WITHOUT_ELSE = 0x02;
    /**
     * Binary operators.
     */
    private static final String[] BINARY_OP = {"==", "!=", "<", "<=", ">", ">=", "+", "-", "*", "/"};
    /**
     * Unary operators.
     */
    private static final String[] UNARY_OP = {"-"};


    /**
     * The random number generator used for random decisions.
     */
    private final Random random;
    /**
     * Indentation depth.
     */
    private int indentation = 0;
    /**
     * Output string builder
     */
    private final StringBuilder sb;
    /**
     * Number of nested loops
     */
    private int loopDepth = 0;
    /**
     * Current scope start in the variables list.
     */
    private int currScopeStart = 0;
    /**
     * The depth of blocks allowed.
     */
    private int depth = 20;
    /**
     * The depth of expressions allowed.
     */
    private int exprDepth = 30;
    /**
     * Current variables in scope.
     */
    private final ArrayList<String> variables = new ArrayList<>();
    /**
     * If this needs to generate valid scripts or is also allowed to generate invalid ones.
     */
    private final boolean generateValid;
    /**
     * If this script might be invalid.
     */
    private boolean maybeInvalid = false;

    /**
     *
     * @param random the random number generator.
     * @param sb the script will be written to this string builder.
     * @param generateValid if the script should be guaranteed valid or if it is allowed to contain bugs.
     */
    public ScriptGenerator(Random random, StringBuilder sb, boolean generateValid) {
        this.random = random;
        this.sb = sb;
        this.generateValid = generateValid;
    }

    /**
     * Random number with log distribution
     * @param max The maximum value
     * @return A random number in the range [0, max) with higher numbers being more uncommon.
     */
    private int randLog(int max) {
        max--;
        int n = 1<<max;
        var num = random.nextInt(n);
        for (int i=max-1; i>=0; i--) {
            if ((num & (1<<i))!=0) return max-i-1;
        }
        return max;
    }

    /**
     * Helper function to print indentation.
     * @return sb for chaining
     */
    private StringBuilder printIndentation() {
        return sb.repeat(' ', indentation);
    }

    /**
     * Generate a random name for variables. This might be invalid when it is a keyword.
     * @return The random name generated
     */
    private StringBuilder getRandomName() {
        int len = random.nextInt(30) + 1;
        StringBuilder sb = new StringBuilder(len);
        sb.append(VAR_CHARS.charAt(random.nextInt(VAR_CHARS.length()-10)));
        for (int i=1; i<len; i++)
            sb.append(VAR_CHARS.charAt(random.nextInt(VAR_CHARS.length())));
        return sb;
    }

    /**
     * Generate a variable name. This might be new random name or a name from an outer scope.
     * If the program is allowed to be invalid this can return keywords and already used variable names in the current scope.
     * @return The variable name.
     */
    private String getVarName() {
        if (currScopeStart > 0 && random.nextInt(10) > 7) {
            // Use a variable outside the current scope.
            var v = variables.get(random.nextInt(currScopeStart));
            if (variables.lastIndexOf(v) < currScopeStart) return v;
            if (!generateValid) {
                maybeInvalid = true;
                return v;
            }
        }
        // Generate a new random variable name
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



    /**
     * Generates a program and writes it into the string builder supplied in the constructor.
     * @return If the program is allowed to be invalid returns if something potentially invalid was generated.
     */
    public boolean genProgram() {
        this.maybeInvalid = false;
        variables.add(ScopeNode.ARG0);
        currScopeStart = variables.size();
        if ((genStatements() & FLAG_STOP) == 0) {
            if (generateValid || random.nextInt(10)<7) {
                genReturn();
            } else {
                this.maybeInvalid = true;
            }
        }
        return !this.maybeInvalid;
    }

    /**
     * Generate a list of statements.
     * @return flags FLAG_STOP and FLAG_IF_WITHOUT_ELSE for the last statement generated
     */
    public int genStatements() {
        var num = random.nextInt(10); // (30);
        for (int i=0; i<num; i++) {
            printIndentation();
            var stop = genStatement();
            sb.append("\n");
            if ((stop & FLAG_STOP) != 0) return stop;
        }
        return 0;
    }

    /**
     * Generate a single statement
     * @return flags FLAG_STOP and FLAG_IF_WITHOUT_ELSE for the generated statement
     */
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

    /**
     * Generate a statement for if, else and while blocks.
     * This is special since it disallows declarations and prefers to generate blocks.
     * @return flags FLAG_STOP and FLAG_IF_WITHOUT_ELSE for the generated statement
     */
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

    /**
     * Generate an exit. This can be return or break and continue when in a loop.
     * @return FLAG_STOP
     */
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

    /**
     * Generate a block statement. After a certain depth only empty blocks are generated to ensure termination of the generator.
     * @return flag FLAG_STOP for the generated statement
     */
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

    /**
     * Generate an if statement with an optional else block.
     * @return flags FLAG_STOP and FLAG_IF_WITHOUT_ELSE for the generated statement
     */
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

    /**
     * Generate a while loop.
     * @return 0
     */
    public int genWhile() {
        sb.append("while(");
        genExpression();
        sb.append(") ");
        loopDepth++;
        genStatementBlock();
        loopDepth--;
        return 0;
    }

    /**
     * Generate a declaration statement.
     * @return 0
     */
    public int genDecl() {
        String name = getVarName();
        sb.append("int ").append(name).append("=");
        genExpression();
        sb.append(";");
        variables.add(name);
        return 0;
    }

    /**
     * Generate an assignment statement.
     * @return 0
     */
    public int genAssignment() {
        if (variables.isEmpty()) return genDecl();
        genVariable();
        sb.append("=");
        genExpression();
        sb.append(";");
        return 0;
    }

    /**
     * Generate a return statement.
     * @return FLAG_STOP
     */
    public int genReturn() {
        sb.append("return ");
        genExpression();
        sb.append(";");
        return FLAG_STOP;
    }


    /**
     * Generate a binary expression.
     * This method does not care about operator precedence.
     */
    public void genExpression() {
        var num = randLog(10);
        while(num-->0) {
            genUnary();
            sb.append(BINARY_OP[random.nextInt(BINARY_OP.length)]);
        }
        genUnary();
    }

    /**
     * Generate a unary expression.
     */
    public void genUnary() {
        var num = randLog(6);
        while(num-->0) {
            sb.append(UNARY_OP[random.nextInt(UNARY_OP.length)]);
        }
        genPrimary();
    }

    /**
     * Generate a primary expression.
     */
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
                    // Ensure termination of the generator.
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

    /**
     * Generate a number.
     */
    public void genNumber() {
        sb.append(random.nextInt(100));
    }

    /**
     * Generate a variable. If there are none generate a number instead.
     * If the program is allowed to be invalid a random name can be generated in some cases.
     */
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
