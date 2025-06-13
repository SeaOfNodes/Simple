package com.seaofnodes.simple.fuzzer;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.ScopeNode;
import com.seaofnodes.simple.type.TypeInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import static com.seaofnodes.simple.Parser.KEYWORDS;


/**
 * Generate a pseudo random script.
 * This generator will generate the same script for two random number generators initialized with the same seed.
 * To generate valid code it implements the parser but instead of parsing it emits the statements and expressions.
 * It is guaranteed to terminate.
 */
public class ScriptGenerator {

    private static final int MAX_STATEMENTS_PER_BLOCK = 5;
    private static final int MAX_NAME_LENGTH = 10;
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
     * Flag for a statement that it does not pass on control flow to the next statement.
     */
    private static final int FLAG_STOP = 0x01;
    /**
     * Flag for a statement that it contains a final if statement without an else branch.
     */
    private static final int FLAG_IF_WITHOUT_ELSE = 0x02;

    private static class Type {
        final String name;
        Type(String name) {this.name=name;}
        boolean isa(Type other) { return this == other; }
    }

    private static class TypeStruct extends Type {
        record Field (String name, Type type, TypeStruct struct) {}
        Field[] fields;
        final TypeNullable nullable = new TypeNullable(this);
        TypeStruct(String name) {
            super(name);
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

    private static final Type TYPE_FLT = new Type("flt");

    private static class TypeInt extends Type {

        TypeInt(String name) {
            super(name);
        }

        @Override
        boolean isa(Type other) {
            return super.isa(other) || other == TYPE_FLT || other instanceof TypeInt;
        }
    }

    private static final List<TypeInt> INTTYPES = new ArrayList<>();

    static {
        for( var e : Parser.TYPES.entrySet() ) {
            if( e.getValue() instanceof TypeInteger ) {
                INTTYPES.add(new TypeInt(e.getKey()));
            }
        }
    }

    private static final Type TYPE_INT = INTTYPES.stream().filter(t->t.name.equals("int")).findAny().get();
    private static final Type TYPE_BOOL = INTTYPES.stream().filter(t->t.name.equals("bool")).findAny().get();

    private static class Variable {
        final String name;
        final Type declared;
        final Variable shadowing;
        Type type;
        boolean shadowed = false;

        Variable(String name, Type type, Variable var) {
            this.name = name;
            this.declared = type;
            this.type = type;
            this.shadowing = var;
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
     * Current variables in scope.
     */
    private final ArrayList<Variable> variables = new ArrayList<>();
    /**
     * All defined structs.
     */
    private final ArrayList<TypeStruct> structs = new ArrayList<>();
    /**
     * All forward declared structs
     */
    private final ArrayList<TypeStruct> forwardStructs = new ArrayList<>();
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

    /**
     * Should invalid code be generated?
     * @return True if invalid code should be generated
     */
    private boolean generateInvalid() {
        if (generateValid || random.nextInt(1000)>2) return false;
        maybeInvalid = true;
        return true;
    }

    /**
     * Lookup a variable by name
     * @param name name of the variable
     * @return The index of the variable in variables or -1
     */
    private int lookup(String name) {
        for (int i=variables.size()-1; i>=0; i--) {
            if (variables.get(i).name.equals(name)) return i;
        }
        return -1;
    }

    /**
     * Lookup a variable by name
     * @param name name of the variable
     * @return THe variable of null if not found
     */
    private Variable lookupVar(String name) {
        int idx = lookup(name);
        return idx == -1 ? null : variables.get(idx);
    }

    /**
     * Add a variable to the current scope
     * @param name The name of the variable
     * @param type The declared type of the variable
     */
    private void addVariable(String name, Type type) {
        var old = lookupVar(name);
        variables.add(new Variable(name, type, old));
        if (old != null) old.shadowed = true;
    }

    /**
     * Pop variables to level to
     * @param to Level until which variables should be popped
     */
    private void popVariables(int to) {
        while (variables.size() > to) {
            var v = variables.removeLast();
            if (v.shadowing != null) v.shadowing.shadowed = false;
        }
    }

    /**
     * Get a random visible variable patching pred
     * @param pred The predicate the variable needs to match
     * @return A random visible variable matching pred or null if no variable mached pred.
     */
    private Variable findVisibleVariablesMatching(Predicate<Variable> pred) {
        int num=0;
        for (var v:variables) {
            if (!v.shadowed && pred.test(v)) num++;
        }
        if (num == 0) return null;
        num = random.nextInt(num);
        for (var v:variables) {
            if (!v.shadowed && pred.test(v) && num-- == 0) return v;
        }
        throw new AssertionError();
    }

    /**
     * Get a structure by name
     * @param name Name of the structure
     * @return The structure of null
     */
    private TypeStruct getStruct(String name) {
        for (var s:structs) {
            if (s.name.equals(name)) return s;
        }
        for (var s:forwardStructs) if (s.name.equals(name)) return s;
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
            if (lookup(v.name) < currScopeStart) return v.name;
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

    private <T> T randFromList(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    /**
     * Get a random structure name
     * @return A random structure name
     */
    private String getStructName() {
        if (!structs.isEmpty() && generateInvalid()) return randFromList(structs).name;
        if (!forwardStructs.isEmpty() && generateInvalid()) return randFromList(forwardStructs).name;

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

    public TypeInt getIntType() {
        return randFromList(INTTYPES);
    }

    /**
     * Get a random type
     * @return A random type
     */
    private Type getType(boolean allowForward) {
        int t = random.nextInt(10);
        if( t<5 || structs.isEmpty() ) return getIntType();
        if( t<7 ) return TYPE_FLT;
        if (allowForward && random.nextInt(10)==0) {
            int i=random.nextInt(forwardStructs.size()+1);
            if (i>0) return forwardStructs.get(i-1).nullable;
            var name = getStructName();
            var struct = new TypeStruct(name);
            forwardStructs.add(struct);
            return struct.nullable;
        }
        TypeStruct struct = randFromList(structs);
        if (random.nextBoolean()) return struct.nullable;
        return struct;
    }

    private Type getType() {
        return getType(false);
    }

    /**
     * Generates a program and writes it into the string builder supplied in the constructor.
     * @return If the program is allowed to be invalid returns if something potentially invalid was generated.
     */
    public boolean genProgram() {
        var oldGenerateValid = generateValid;
        if (!generateValid && random.nextBoolean()) generateValid = true;
        this.maybeInvalid = false;
        addVariable(ScopeNode.ARG0, TYPE_INT);
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
        return switch (random.nextInt(12)) {
            case 0 -> allowStructs || generateInvalid() ? genStruct() : genAssignment();
            case 1 -> genBlock();
            case 2 -> genIf();
            case 3 -> genCountedLoop();
            case 4 -> genWhile();
            case 5, 6 -> genDecl();
            case 7, 8, 9 -> genAssignment();
            case 10 -> genNullCheck();
            default -> genExit();
        };
    }

    /**
     * Generate a new structure
     * @return 0
     */
    public int genStruct() {
        int idx = random.nextInt(forwardStructs.size()+10);
        TypeStruct struct;
        if (idx < 10) {
            var name = getStructName();
            struct = new TypeStruct(name);
            forwardStructs.add(struct);
        } else {
            struct = forwardStructs.get(idx-10);
        }
        var fields = new TypeStruct.Field[generateInvalid() ? 0 : random.nextInt(10)];
        sb.append("struct ").append(struct.name).append(" {\n");
        indentation += INDENTATION;
        for (int i=0; i<fields.length; i++) {
            var fieldName = getRandomName();
            var type = getType(true);
            printIndentation().append(type.name).append(" ").append(fieldName).append(";\n");
            fields[i] = new TypeStruct.Field(fieldName.toString(), type, struct);
        }
        indentation -= INDENTATION;
        printIndentation().append("};");
        struct.fields = fields;
        forwardStructs.remove(struct);
        structs.add(struct);
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
            res = switch (random.nextInt(12)) {
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
                case 10 -> genNullCheck();
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
        popVariables(currScopeStart);
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
        genExpression(TYPE_BOOL, true, true);
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
        var v = findVisibleVariablesMatching(va->va.declared instanceof TypeNullable);
        if (v == null) return genIf();
        var n = (TypeNullable)v.declared;
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

    /**
     * Generate a while loop.
     * @return 0
     */
    public int genWhile() {
        sb.append("while(");
        genExpression(TYPE_BOOL, true, true);
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
        genExpression(TYPE_INT, true, false);
        sb.append(";\n");
        addVariable(name, TYPE_INT);
        printIndentation().append("while(").append(name).append("<");
        genShift(TYPE_INT, true, false, MAX_EXPRESSION_DEPTH);
        sb.append(") {\n");
        indentation += INDENTATION;
        depth--;
        loopDepth++;
        printIndentation().append(name).append("=").append(name).append("+");
        genExpression(TYPE_INT, true, false);
        sb.append(";\n");
        genStatements();
        loopDepth--;
        depth++;
        indentation -= INDENTATION;
        printIndentation().append("}\n");
        indentation -= INDENTATION;
        printIndentation().append("}");
        popVariables(currScopeStart);
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
        // Always make them mutable
        sb.append(generateInvalid() ? getRandomName() : type.name).append(" !").append(name);
        if (!(type instanceof TypeNullable) || random.nextBoolean()) {
            sb.append("=");
            genExpression(type, true, type==TYPE_BOOL);
        }
        sb.append(";");
        addVariable(name, type);
        return 0;
    }

    /**
     * Generate an assignment statement.
     * @return 0
     */
    public int genAssignment() {
        Type type;
        Type declared;
        if (generateInvalid()) {
            sb.append(getRandomName());
            type = getType();
            declared = type;
        } else {
            var v = findVisibleVariablesMatching(va->true);
            if (v == null) return genDecl();
            sb.append(v.name);
            type = v.type;
            declared = v.declared;
        }
        if (generateInvalid()) {
            var name = getRandomName();
            sb.append(".").append(name);
            declared = getType();
        } else if (type instanceof TypeStruct s && s.fields.length > 0 && random.nextBoolean()) {
            var field = s.fields[random.nextInt(s.fields.length)];
            sb.append(".").append(field.name);
            declared = field.type;
        }
        sb.append("=");
        genExpression(declared, true, declared==TYPE_BOOL);
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
        genExpression(type, true, false);
        sb.append(";");
        return FLAG_STOP;
    }

    public void genExpression(Type type, boolean change, boolean preferLogical) {
        genExpression(type, change, preferLogical, MAX_EXPRESSION_DEPTH);
    }

    public void genExpression(Type type, boolean change, boolean preferLogical, int d) {
        if (change && generateInvalid()) type = getType();
        if (d > 0 && random.nextInt(10)==0) {
            genLogical(TYPE_INT, true, true, d);
            sb.append("?");
            genExpression(type, true, preferLogical, d-1);
            sb.append(":");
            genExpression(type, false, preferLogical, d-1);
        } else {
            genLogical(type, false, preferLogical, d);
        }
    }

    public void genLogical(Type type, boolean change, boolean preferLogical, int d) {
        if (change && generateInvalid()) type = getType();
        genBitwise(type, false, preferLogical, d);
        while (d-->0 && random.nextInt(preferLogical?5:50) == 0) {
            sb.append(random.nextBoolean()?"&&":"||");
            genBitwise(type, true, preferLogical, d);
        }
    }

    public void genBitwise(Type type, boolean change, boolean preferLogical, int d) {
        if (change && generateInvalid()) type = getType();
        genComparison(type, false, preferLogical, d);
        while (type instanceof TypeInt && d-->0 && random.nextInt(preferLogical?50:5)==0) {
            sb.append("&|^".charAt(random.nextInt(3)));
            genComparison(type, true, preferLogical, d);
        }
    }

    public void genComparison(Type type, boolean change, boolean preferLogical, int d) {
        if (change && generateInvalid()) type = getType();
        if (d > 0 && type instanceof TypeInt && random.nextInt(preferLogical?5:50)==0) {
            type = getType();
            genComparison(type, true, preferLogical, d-1);
            sb.append(random.nextBoolean()?"==":"!=");
        }
        genChaining(type, false, preferLogical, d);
    }

    public void genChaining(Type type, boolean change, boolean preferLogical, int d) {
        if (change && generateInvalid()) type = getType();
        if (d>0 && type instanceof TypeInt && random.nextInt(preferLogical?5:50)==0) {
            genShift(TYPE_INT, true, false, d);
            char c = random.nextBoolean()?'>':'<';
            sb.append(c);
            if (random.nextInt(4)==0) sb.append('=');
            genShift(TYPE_INT, true, false, --d);
            while (d-->0 && random.nextInt(preferLogical?5:50)==0) {
                if (generateInvalid()) c = c=='>' ? '<' : '>';
                sb.append(c);
                if (random.nextInt(4)==0) sb.append('=');
                genShift(TYPE_INT, true, false, d);
            }
        } else {
            genShift(type, false, preferLogical, d);
        }
    }

    private static final String[] SHIFTS = {">>", ">>>", "<<"};

    public void genShift(Type type, boolean change, boolean preferLogical, int d) {
        if (change && generateInvalid()) type = getType();
        genAddition(type, false, preferLogical, d);
        while (d-- > 0 && type instanceof TypeInt && random.nextInt(50)==0) {
            sb.append(SHIFTS[random.nextInt(3)]);
            genAddition(type, true, preferLogical, d);
        }
    }

    public void genAddition(Type type, boolean change, boolean preferLogical, int d) {
        if (change && generateInvalid()) type = getType();
        genMultiplication(type, false, preferLogical, d);
        while (d-- > 0 && (type instanceof TypeInt || type==TYPE_FLT) && random.nextInt(50)==0) {
            sb.append(random.nextBoolean()?" + ":" - ");
            genMultiplication(type, true, preferLogical, d);
        }
    }

    public void genMultiplication(Type type, boolean change, boolean preferLogical, int d) {
        if (change && generateInvalid()) type = getType();
        genUnary(type, false, preferLogical, d);
        while (d-- > 0 && (type instanceof TypeInt || type==TYPE_FLT) && random.nextInt(50)==0) {
            sb.append(random.nextBoolean()?'*':'/');
            genUnary(type, true, preferLogical, d);
        }
    }

    /**
     * Generate a unary expression.
     */
    public void genUnary(Type type, boolean change, boolean preferLogical, int d) {
        if (change && generateInvalid()) type = getType();
        if (!(type instanceof TypeInt) && type != TYPE_FLT) {
            genSuffix(type, false, preferLogical, d);
            return;
        }
        while (d>0 && random.nextInt(5)==0) {
            d--;
            boolean what = type instanceof TypeInt && random.nextInt(preferLogical?2:20) == 0;
            sb.append(what?'!':'-');
        }
        if (random.nextInt(10)==0) {
            var t = type;
            var v = findVisibleVariablesMatching(va->va.type.isa(t));
            if (v != null) {
                sb.append(random.nextBoolean()?"--":"++").append(v.name);
                return;
            }
        }
        genSuffix(type, false, preferLogical, d);
    }

    public TypeStruct.Field getField(Type type) {
        int n=0;
        for (var s: structs) {
            for (var f: s.fields) {
                if (f.type.isa(type)) n++;
            }
        }
        if (n==0) return null;
        n = random.nextInt(n);
        for (var s: structs) {
            for (var f: s.fields) {
                if (f.type.isa(type)) if (n--==0) return f;
            }
        }
        throw new AssertionError();
    }

    public void genSuffix(Type type, boolean change, boolean preferLogical, int d) {
        if (change && generateInvalid()) type = getType();
        if (d > 0 && random.nextBoolean() && genField(type, d-1)) return;
        if (d > 0 && random.nextInt(10) == 0) {
            if (random.nextBoolean() && genField(type, d-1)) return;
            var t = type;
            var v = findVisibleVariablesMatching(va -> va.type.isa(t));
            if (v != null) {
                sb.append(v.name).append(random.nextBoolean() ? "--" : "++");
                return;
            }
        }
        genPrimary(type, false, preferLogical, d);
    }

    public boolean genField(Type type, int d) {
        if (generateInvalid()) {
            var field = getRandomName();
            var t = getType();
            genSuffix(t, true, false, d);
            sb.append(".").append(field);
        } else {
            var field = getField(type);
            if (field == null) return false;
            genSuffix(field.struct, true, false, d);
            sb.append(".").append(field.name);
        }
        return true;
    }

    /**
     * Generate a primary expression.
     */
    public void genPrimary(Type type, boolean change, boolean preferLogical, int d) {
        if (change && generateInvalid()) type = getType();
        var rand = random.nextInt(10);
        if (d > 0 && rand == 0) {
            sb.append("(");
            genExpression(type, false, preferLogical, d-1);
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
        if (type instanceof TypeInt || (type == TYPE_FLT && random.nextBoolean())) {
            var rand = random.nextInt(10);
            switch (rand) {
                case 0 -> sb.append("true");
                case 1 -> sb.append("false");
                default -> sb.append(random.nextInt(1<<(rand-2)));
            }
        } else if (type == TYPE_FLT) {
            sb.append(random.nextFloat());
        } else if (type instanceof TypeNullable n) {
            if ((n.base instanceof TypeStruct s && s.fields == null) || random.nextBoolean()) {
                sb.append("null");
            } else {
                sb.append("new ").append(generateInvalid() ? getRandomName() : n.base.name);
            }
        } else {
            sb.append("new ").append(generateInvalid() ? getRandomName() : type.name);
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
        var t = type;
        var v = findVisibleVariablesMatching(va->va.type.isa(t));
        if (v == null) genConst(type);
        else sb.append(v.name);
    }

}