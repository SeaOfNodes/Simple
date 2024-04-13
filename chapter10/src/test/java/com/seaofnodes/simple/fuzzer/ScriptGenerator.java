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

    private static final int MAX_STATEMENTS_PER_BLOCK = 5;
    private static final int MAX_BINARY_EXPRESSIONS_PER_EXPRESSION = 10;
    private static final int MAX_UNARY_EXPRESSIONS_PER_EXPRESSION = 6;
    private static final int MAX_NAME_LENGTH = 30;
    private static final int MAX_BLOCK_DEPTH = 4;
    private static final int MAX_EXPRESSION_DEPTH = 10;

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

    private static class Type {
        final String name;
        Type(String name) {this.name=name;}
        boolean isa(Type other) { return this == other; }
    }

    private static class TypeStruct extends Type {
        record Field (String name, Type type) {}
        final Field[] fields;
        final TypeNullable nullable = new TypeNullable(this);
        TypeStruct(String name, Field[] fields) {
            super(name);
            this.fields = fields;
        }
        boolean isa(Type other) { return this == other || other == nullable; }
    }

    private static class TypeNullable extends Type {

        final Type base;

        TypeNullable(Type base) {
            super(base.name+"?");
            this.base = base;
        }

    }

    private static final Type TYPE_INT = new Type("int");


    private static class Variable {
        final String name;
        final Type declared;
        Type type;

        Variable(String name, Type type) {
            this.name = name;
            this.declared = type;
            this.type = type;
        }
    }

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
    private int depth = MAX_BLOCK_DEPTH;
    /**
     * The depth of expressions allowed.
     */
    private int exprDepth = MAX_EXPRESSION_DEPTH;
    /**
     * Current variables in scope.
     */
    private final ArrayList<Variable> variables = new ArrayList<>();
    /**
     * All defined structs.
     */
    private final ArrayList<TypeStruct> structs = new ArrayList<>();
    /**
     * Allow to declare structs.
     */
    private boolean allowStructs = true;
    /**
     * If this needs to generate valid scripts or is also allowed to generate invalid ones.
     */
    private boolean generateValid;
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
        int len = random.nextInt(MAX_NAME_LENGTH) + 1;
        StringBuilder sb = new StringBuilder(len);
        sb.append(VAR_CHARS.charAt(random.nextInt(VAR_CHARS.length()-10)));
        for (int i=1; i<len; i++)
            sb.append(VAR_CHARS.charAt(random.nextInt(VAR_CHARS.length())));
        return sb;
    }

    private boolean generateInvalid() {
        if (generateValid || random.nextInt(1000)>2) return false;
        maybeInvalid = true;
        return true;
    }

    private int lookup(String name) {
        for (int i=variables.size()-1; i>=0; i--) {
            if (variables.get(i).name.equals(name)) return i;
        }
        return -1;
    }

    private TypeStruct getStruct(String name) {
        for (var s:structs) {
            if (s.name.equals(name)) return s;
        }
        return null;
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
            if (variables.lastIndexOf(v) < currScopeStart) return v.name;
            if (!generateValid) {
                maybeInvalid = true;
                return v.name;
            }
        }
        // Generate a new random variable name
        StringBuilder sb = getRandomName();
        var v = sb.toString();
        if (!generateValid && (KEYWORDS.contains(v) || lookup(v) >= currScopeStart)) {
            maybeInvalid = true;
            return v;
        }
        while (KEYWORDS.contains(v) || lookup(v) >= currScopeStart) {
            sb.append(VAR_CHARS.charAt(random.nextInt(VAR_CHARS.length())));
            v = sb.toString();
        }
        return v;
    }

    private String getStructName() {
        if (!structs.isEmpty() && generateInvalid()) return structs.get(random.nextInt(structs.size())).name;
        // Generate a new random struct name
        StringBuilder sb = getRandomName();
        var v = sb.toString();
        if (!generateValid && (KEYWORDS.contains(v) || getStruct(v) != null)) {
            maybeInvalid = true;
            return v;
        }
        while (KEYWORDS.contains(v) || getStruct(v) != null) {
            sb.append(VAR_CHARS.charAt(random.nextInt(VAR_CHARS.length())));
            v = sb.toString();
        }
        return v;
    }

    private Type getType() {
        if (structs.isEmpty() || random.nextBoolean()) return TYPE_INT;
        var idx = random.nextInt(structs.size());
        TypeStruct struct = structs.get(idx);
        if (random.nextBoolean()) return struct.nullable;
        return struct;
    }

    /**
     * Generates a program and writes it into the string builder supplied in the constructor.
     * @return If the program is allowed to be invalid returns if something potentially invalid was generated.
     */
    public boolean genProgram() {
        var oldGenerateValid = generateValid;
        if (!generateValid && random.nextBoolean()) generateValid = true;
        this.maybeInvalid = false;
        variables.add(new Variable(ScopeNode.ARG0, TYPE_INT));
        currScopeStart = variables.size();
        if ((genStatements() & FLAG_STOP) == 0) {
            if (generateValid || random.nextInt(10)<7) {
                genReturn();
            } else {
                this.maybeInvalid = true;
            }
        }
        generateValid = oldGenerateValid;
        return !this.maybeInvalid;
    }

    /**
     * Generate a list of statements.
     * @return flags FLAG_STOP and FLAG_IF_WITHOUT_ELSE for the last statement generated
     */
    public int genStatements() {
        var num = random.nextInt(MAX_STATEMENTS_PER_BLOCK * (depth+1));
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
        return switch (random.nextInt(11)) {
            case 0 -> allowStructs || generateInvalid() ? genStruct() : genAssignment();
            case 1 -> genBlock();
            case 2 -> genIf();
            case 3 -> genCountedLoop();
            case 4 -> genWhile();
            case 5, 6 -> genDecl();
            case 7, 8, 9 -> genAssignment();
            default -> genExit();
        };
    }

    public int genStruct() {
        var name = getStructName();
        var fields = new TypeStruct.Field[generateInvalid() ? 0 : random.nextInt(10)];
        sb.append("struct ").append(name).append(" {\n");
        indentation += INDENTATION;
        for (int i=0; i<fields.length; i++) {
            var fieldName = getRandomName();
            var type = generateInvalid() ? getType() : TYPE_INT;
            printIndentation().append(type.name).append(" ").append(fieldName).append(";\n");
            fields[i] = new TypeStruct.Field(fieldName.toString(), type);
        }
        indentation -= INDENTATION;
        printIndentation().append("}");
        structs.add(new TypeStruct(name, fields));
        return 0;
    }

    /**
     * Generate a statement for if, else and while blocks.
     * This is special since it disallows declarations and prefers to generate blocks.
     * @return flags FLAG_STOP and FLAG_IF_WITHOUT_ELSE for the generated statement
     */
    public int genStatementBlock() {
        var oldAllowStructs = allowStructs;
        allowStructs = false;
        indentation += INDENTATION;
        int res;
        if (depth == 0) {
            res = random.nextBoolean() ? genAssignment() : genExit();
        } else {
            depth--;
            res = switch (random.nextInt(11)) {
                case 1, 2, 3, 4, 5 -> {
                    depth++;
                    indentation -= INDENTATION;
                    var ret = genBlock();
                    indentation += INDENTATION;
                    depth--;
                    yield ret;
                }
                case 6 -> genIf();
                case 7 -> genCountedLoop();
                case 8 -> genWhile();
                case 9 -> genAssignment();
                default -> genExit();
            };
            depth++;
        }
        indentation -= INDENTATION;
        allowStructs = oldAllowStructs;
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
        var oldAllowStructs = allowStructs;
        var oldCSS = currScopeStart;
        allowStructs = false;
        currScopeStart = variables.size();
        depth--;
        sb.append("{\n");
        indentation += INDENTATION;
        var stop = genStatements();
        indentation -= INDENTATION;
        printIndentation().append("}");
        while (variables.size() > currScopeStart)
            variables.removeLast();
        depth++;
        currScopeStart = oldCSS;
        allowStructs = oldAllowStructs;
        return stop & ~FLAG_IF_WITHOUT_ELSE;
    }

    /**
     * Generate an if statement with an optional else block.
     * @return flags FLAG_STOP and FLAG_IF_WITHOUT_ELSE for the generated statement
     */
    public int genIf() {
        sb.append("if(");
        genExpression(TYPE_INT);
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
     * Generate a block with a null check
     * if (nullable) { handle_nullable_as_non_nullable; }
     */
    public int genNullCheck() {
        int num = 0;
        for (var v : variables) {
            if (v.declared instanceof TypeNullable) num++;
        }
        if (num == 0) return genIf();
        var idx = random.nextInt(num);
        for (var v:variables) {
            if (v.declared instanceof TypeNullable n) {
                if (idx == 0) {
                    boolean negate = random.nextBoolean();
                    sb.append("if(");
                    if (negate) sb.append("!");
                    sb.append(v.name).append(")");
                    if (!negate) v.type = n.base;
                    var stop = genStatementBlock();
                    v.type = n;
                    if ((stop & FLAG_IF_WITHOUT_ELSE) == 0 && random.nextInt(10) > 3) {
                        sb.append("\n");
                        printIndentation().append("else ");
                        if (negate) v.type = n.base;
                        stop &= genStatementBlock();
                        v.type = n;
                    } else {
                        stop = FLAG_IF_WITHOUT_ELSE;
                    }
                    return stop;
                }
                idx--;
            }
        }
        throw new AssertionError();
    }

    /**
     * Generate a while loop.
     * @return 0
     */
    public int genWhile() {
        sb.append("while(");
        genExpression(TYPE_INT);
        sb.append(") ");
        loopDepth++;
        genStatementBlock();
        loopDepth--;
        return 0;
    }

    /**
     * Generate a counted loop of the form
     * <code>
     *     {
     *         int varname = start_expr;
     *         while (varname < end_expr) loop_body;
     *     }
     * </code>
     * @return 0
     */
    public int genCountedLoop() {
        if (depth == 0) return genWhile();
        var oldCSS = currScopeStart;
        currScopeStart = variables.size();
        sb.append("{\n");
        indentation += INDENTATION;
        String name = getVarName();
        printIndentation().append("int ").append(name).append("=");
        genExpression(TYPE_INT);
        sb.append(";\n");
        variables.add(new Variable(name, TYPE_INT));
        printIndentation().append("while(").append(name).append("<");
        genExpression(TYPE_INT);
        sb.append(") {\n");
        indentation += INDENTATION;
        depth--;
        loopDepth++;
        printIndentation().append(name).append("=").append(name).append("+");
        genExpression(TYPE_INT);
        sb.append(";\n");
        genStatements();
        loopDepth--;
        depth++;
        indentation -= INDENTATION;
        printIndentation().append("}\n");
        indentation -= INDENTATION;
        printIndentation().append("}");
        while (variables.size() > currScopeStart)
            variables.removeLast();
        currScopeStart = oldCSS;
        return 0;
    }

    /**
     * Generate a declaration statement.
     * @return 0
     */
    public int genDecl() {
        var type = getType();
        var name = getVarName();
        sb.append(generateInvalid() ? getRandomName() : type.name).append(" ").append(name);
        if (type == TYPE_INT || random.nextBoolean()) {
            sb.append("=");
            genExpression(type);
        }
        sb.append(";");
        variables.add(new Variable(name, type));
        return 0;
    }

    /**
     * Generate an assignment statement.
     * @return 0
     */
    public int genAssignment() {
        if (variables.isEmpty()) return genDecl();
        Type type;
        if (generateInvalid()) {
            sb.append(getRandomName());
            type = getType();
        } else {
            var variable = variables.get(random.nextInt(variables.size()));
            sb.append(variable.name);
            type = variable.declared;
        }
        if (generateInvalid()) {
            var name = getRandomName();
            sb.append(".").append(name);
            type = getType();
        } else if (type instanceof TypeStruct s && s.fields.length > 0 && random.nextBoolean()) {
            var field = s.fields[random.nextInt(s.fields.length)];
            sb.append(".").append(field.name);
            type = field.type;
        }
        sb.append("=");
        genExpression(type);
        sb.append(";");
        return 0;
    }

    /**
     * Generate a return statement.
     * @return FLAG_STOP
     */
    public int genReturn() {
        var type = getType();
        sb.append("return ");
        genExpression(type);
        sb.append(";");
        return FLAG_STOP;
    }

    /**
     * Generate a binary expression.
     * This method does not care about operator precedence.
     */
    public void genExpression(Type type) {
        if (generateInvalid()) type = getType();
        if (type != TYPE_INT) {
            genUnary(type);
            return;
        }
        var num = randLog(MAX_BINARY_EXPRESSIONS_PER_EXPRESSION);
        while(num-->0) {
            genUnary(TYPE_INT);
            sb.append(BINARY_OP[random.nextInt(BINARY_OP.length)]);
        }
        genUnary(TYPE_INT);
    }

    /**
     * Generate a unary expression.
     */
    public void genUnary(Type type) {
        if (generateInvalid()) type = getType();
        if (type != TYPE_INT) {
            genSuffix(type);
            return;
        }
        var num = randLog(MAX_UNARY_EXPRESSIONS_PER_EXPRESSION);
        while(num-->0) {
            sb.append(UNARY_OP[random.nextInt(UNARY_OP.length)]);
        }
        genSuffix(TYPE_INT);
    }

    /**
     * Generate
     */
    public void genSuffix(Type type) {
        if (generateInvalid()) type = getType();
        if (type == TYPE_INT && random.nextBoolean()) {
            var t = getType();
            if (generateInvalid()) {
                var field = getRandomName();
                genPrimary(t);
                sb.append(".").append(field);
                return;
            } else if (t instanceof TypeStruct s && s.fields.length > 0) {
                var field = s.fields[random.nextInt(s.fields.length)];
                if (field.type == TYPE_INT) {
                    genPrimary(t);
                    sb.append(".").append(field.name);
                    return;
                }
            }
        }
        genPrimary(type);
    }

    /**
     * Generate a primary expression.
     */
    public void genPrimary(Type type) {
        if (generateInvalid()) type = getType();
        var rand = random.nextInt(10);
        if (rand == 0 && exprDepth != 0) {
            sb.append("(");
            exprDepth--;
            genExpression(type);
            exprDepth++;
            sb.append(")");
        } else if (rand < 6) {
            genVariable(type);
        } else {
            genConst(type);
        }
    }

    /**
     * Generate a constant.
     */
    public void genConst(Type type) {
        if (generateInvalid()) type = getType();
        if (type == TYPE_INT) {
            var rand = random.nextInt(10);
            switch (rand) {
                case 0 -> sb.append("true");
                case 1 -> sb.append("false");
                default -> sb.append(random.nextInt(1<<(rand-2)));
            }
        } else {
            if (type instanceof TypeNullable && random.nextBoolean()) {
                sb.append("null");
            } else {
                sb.append("new ").append(generateInvalid() ? getRandomName() : type.name);
            }
        }
    }

    /**
     * Generate a variable. If there are none generate a number instead.
     * If the program is allowed to be invalid a random name can be generated in some cases.
     */
    public void genVariable(Type type) {
        if (generateInvalid()) {
            sb.append(getRandomName());
            return;
        }
        if (generateInvalid()) type = getType();
        int num = 0;
        for (var v:variables) {
            if (v.type.isa(type)) num++;
        }
        if (num == 0) {
            genConst(type);
        } else {
            var idx = random.nextInt(num);
            for (var v:variables) {
                if (v.type.isa(type)) {
                    if (idx == 0) {
                        sb.append(v.name);
                        return;
                    }
                    idx--;
                }
            }
            throw new AssertionError();
        }
    }

}
