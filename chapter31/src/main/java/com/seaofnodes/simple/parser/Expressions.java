package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.parser.Parser.ParseException;
import com.seaofnodes.simple.type.*;

import static com.seaofnodes.simple.util.Utils.TODO;

/** Recursive-descent expression precedence, from assignment down to primaries. */
final class Expressions {
    private final Parser P;

    Expressions(Parser P) { this.P = P; }

    /** Parse: [name '='] expr
     */
    Node parseAssignment() {
        P._lexer.skipWhiteSpace();
        Lexer site=P.loc();
        int old = P.pos();
        String name = P._lexer.matchId();
        // Just a plain expression, no assignment.
        // Distinguish `var==expr` from `var=expr`
        if( name==null || Parser.KEYWORDS.contains(name) || !P.matchOpx('=','=') )
            {  P.pos(old);  return parseExpression();  }

        // Find variable to update
        Var def = P._scope.lookup(name);
        if( def==null ) {
            P.pos(old);
            P._lexer.skipWhiteSpace();
            throw P.error("Undefined name '" + name + "'");
        }

        if( def._extern!=0 )    // Parse the C binding as a memory lvalue
            {  P.pos(old);  return parseExpression();  }

        P.references.checkAssignment(def,site);
        return P.references.assign(def,parseAssignment());
    }

    /**
     * Parse an expression of the form:
     *
     * <pre>
     *     expression : logicalExpression ('?' assignment (':' assignment)?)?
     * </pre>
     * @return an expression {@link Node}, never {@code null}
     */
    private Node parseExpression() {
        Node expr = parseLogical();
        return P.match("?") ? P.controlflow.conditional(expr,":") : expr;
    }

    /**
     * Parse a logical expression
     *
     * <pre>
     *     logicalExpression : bitwiseExpression (('&&' | '||') logicalExpression)?
     * </pre>
     * @return a bitwise expression {@link Node}, never {@code null}
     */

    Node parseLogical() {
        Node lhs = parseBitwise();
        while (true) {
            if( P.match("&&") ) lhs = P.controlflow.conditional(lhs, "&&");
            else if( P.match("||") ) lhs = P.controlflow.conditional(lhs, "||");
            else break;
        }
        return lhs;
    }

    private Node parseBitwise() {
        Node lhs = parseEquality();
        while( true ) {
            if( P.matchOp('&') ) lhs = new AndNode(P.loc(),lhs,null);
            else if( P.matchOp('|') ) lhs = new  OrNode(P.loc(),lhs,null);
            else if( P.match  ("^") ) lhs = new XorNode(P.loc(),lhs,null);
            else break;
            lhs.setDef(2,parseEquality());
            lhs = P.peep(lhs);
        }
        return lhs;
    }

    /**
     * Parse an eq/ne expression
     */
    private Node parseEquality() {
        Node lhs = parseComparison();
        while( true ) {
            boolean eq = false;
            if( P.match("==") ) eq = true;
            else if( !P.match("!=") ) break;
            lhs.keep();
            Node rhs = parseComparison();
            lhs = P.peep(new BoolNode.EQ(lhs.unkeep(),rhs));
            if( !eq ) lhs = P.peep(new NotNode(lhs));
        }
        return lhs;
    }

    /**
     * Parse an expression of the form:
     *
     * <pre>
     *     expr : shiftExpr < shiftExpr <= shiftExpr...
     *     expr : shiftExpr > shiftExpr >= shiftExpr...
     * </pre>
     * @return an comparator expression {@link Node}, never {@code null}
     */
    private Node parseComparison() {
        Node lhs = parseShift();
        int dir = parseCompDir();
        // No compare
        if( dir==0 ) return lhs;
        lhs.keep();

        // Compare
        Node rhs = parseShift().keep();
        Node cmp = makeCompBool(dir,lhs.unkeep(),rhs).keep(); // Convert to a bool

        // Stacked compares?
        P._lexer.skipWhiteSpace();
        Lexer operator=P.loc();
        int dir0 = parseCompDir();
        if( dir0 == 0 ) {
            rhs.unkill();
            return cmp.unkeep();
        }

        // Keep the middle operand separately from the boolean result.
        // The next test compares adjacent operands, not that boolean.
        Node ifNode = new IfNode(P.ctrl(), cmp.unkeep()).peephole();
        Node ifT = new CProjNode(ifNode.  keep(), 0, "True" ).peephole().keep();
        Node ifF = new CProjNode(ifNode.unkeep(), 1, "False").peephole();
        // False side does nothing but capture memory & side-effects
        ScopeNode fail = P._scope.dup();
        fail.ctrl(ifF);

        // Loop over stacked compares.  Each loop moves the old RHS to become
        // the new LHS, so e.g. (a < b < c < d) compares (LHS:a < RHS:b) then
        // compares (LHS:b < RHS:c) then (LHS:c < RHS:d), etc.
        while( dir0 != 0 ) {
            if( Math.abs(dir) != Math.abs(dir0) )
                throw Parser.error("Mixing relational directions in a chained relational test",operator);
            // True side parses next arm of test
            P.ctrl(ifT.unkeep());
            lhs = rhs;          // Reuse the previous RHS exactly once
            rhs = parseShift().keep();
            cmp = makeCompBool(dir0,lhs.unkeep(),rhs); // Convert to a bool
            ifNode = new IfNode(P.ctrl(), cmp).peephole();
            ifT = new CProjNode(ifNode.  keep(), 0, "True" ).peephole().keep();
            ifF = new CProjNode(ifNode.unkeep(), 1, "False").peephole();
            // Merge result into the fail case
            P.ctrl(ifF);
            fail.mergeScopes(P._scope.dup(), P.loc()).peephole();
            // Check next stacked compare
            P._lexer.skipWhiteSpace();
            operator=P.loc();
            dir0 = parseCompDir();
        }
        rhs.unkill();
        P.ctrl(ifT.unkeep());
        RegionNode r = fail.mergeScopes(P._scope, P.loc()).init();
        P._scope = fail;
        return new PhiNode("", r,P.con(0),P.con(1)).peephole();
    }

    private int parseCompDir() {
        if( P.match("<=") ) return -1; // Negative for "equals-or-less"
        if( P.match(">=") ) return -2; // 1 for <, 2 for >
        if( P.match("<" ) ) return  1; // Positive for just "less"
        if( P.match(">" ) ) return  2;
        return 0;
    }

    private Node makeCompBool( int dir, Node lhs, Node rhs ) {
        if( Math.abs(dir)==2 )
            { Node tmp = lhs; lhs = rhs; rhs = tmp; }
        Node cmp = dir < 0 ? new BoolNode.LE(lhs,rhs) : new BoolNode.LT(lhs,rhs);
        return P.peep(cmp);
    }

    /**
     * Parse an additive expression
     *
     * <pre>
     *     shiftExpr : additiveExpr (('<<' | '>>' | '>>>') additiveExpr)*
     * </pre>
     * @return a shift expression {@link Node}, never {@code null}
     */
    private Node parseShift() {
        Node lhs = parseAddition();
        while( true ) {
            P._lexer.skipWhiteSpace();
            Lexer site=P.loc();
            if( P.match("<<") ) lhs = new ShlNode(site,lhs,null);
            else if( P.match(">>>")) lhs = new ShrNode(site,lhs,null);
            else if( P.match(">>") ) lhs = new SarNode(site,lhs,null);
            else break;
            lhs.setDef(2,parseAddition());
            ParseException err;
            if( (err = lhs.err()) != null )
                throw err;
            lhs = P.peep(lhs);
        }
        return lhs;
    }

    /**
     * Parse an additive expression
     *
     * <pre>
     *     additiveExpr : multiplicativeExpr (('+' | '-') multiplicativeExpr)*
     * </pre>
     * @return an add expression {@link Node}, never {@code null}
     */
    private Node parseAddition() {
        Node lhs = parseMultiplication();
        while( true ) {
            if( P.match("+") ) lhs = new AddNode(lhs,null);
            else if( P.match("-") ) lhs = new SubNode(lhs,null);
            else break;
            lhs.setDef(2,parseMultiplication());
            lhs = P.peep(lhs);
        }
        return lhs;
    }

    /**
     * Parse an multiplicativeExpr expression
     *
     * <pre>
     *     multiplicativeExpr : unaryExpr (('*' | '/') unaryExpr)*
     * </pre>
     * @return a multiply expression {@link Node}, never {@code null}
     */
    private Node parseMultiplication() {
        var lhs = parseUnary();
        while( true ) {
            if( P.match("*") ) lhs = new MulNode(lhs,null);
            else if( P.match("/") ) lhs = new DivNode(lhs,null);
            else break;
            lhs.setDef(2,parseUnary());
            lhs = P.peep(lhs);
        }
        return lhs;
    }

    /**
     * Parse a unary minus expression.
     *
     * <pre>
     *     unaryExpr : ('-') unaryExpr | '!') unaryExpr | postfixExpr | primaryExpr | '--' Id | '++' Id
     * </pre>
     * @return a unary expression {@link Node}, never {@code null}
     */
    private Node parseUnary() {
        // Pre-dec/pre-inc
        int old = P.pos();
        if( P.match("--") || P.match("++") ) {
            int delta = P._lexer.peek(-1)=='+' ? 1 : -1; // Inc vs Dec
            P._lexer.skipWhiteSpace();
            int start = P.pos();
            Lexer site=P.loc();
            String name = P._lexer.matchId();
            Var var = name==null ? null : P._scope.lookup(name);
            if( var!=null && !(var.type() instanceof TypeMemPtr) ) {
                if( var._extern==0 && var._final && !P._scope.canInit(var) )
                    throw Parser.error("Cannot reassign final '"+var._name+"'",site);
                // Resolve fields just as a read or postfix update does. Updating
                // their declaration's local slot would silently lose the Store.
                P.pos(start);
                return parsePrimary(delta);
            }
            // Reset, try again
            P.pos(old);
        }
        if (P.match("-")) return P.peep(new MinusNode(parseUnary()));
        if (P.match("!")) return P.peep(new   NotNode(parseUnary()));
        // Else be a primary expression
        return parsePrimary();
    }

    /**
     * Parse a primary expression:
     *
     * <pre>
     *     primaryExpr : integerLiteral | "string" | 'char' | true | false | null |
     *                   new Type | '(' expression ')' | Id['++','--'] |
     *                   type['.' Id]*
     * </pre>
     * @return a primary {@link Node}, never {@code null}
     */
    private Node parsePrimary() { return parsePrimary(0); }

    // pre is +/-1 for a prefix update of a known name, zero otherwise.
    private Node parsePrimary(int pre) {
        if( P._lexer.isNumber(P._lexer.peek()) ) return parseLiteral();
        if( P._lexer.peek('"') ) return parsePostfix(P.strings.parse());
        if( P.matchx("true" ) ) return P.con(1);
        if( P.matchx("false") ) return P._code.ZERO;
        if( P.matchx("null" ) ) return P.con(Type.NIL);
        if( P.match ("'"    ) ) return parseChar();
        if( P.match ("("    ) ) return parsePostfix(P.require(parseAssignment(), ")"));
        if( P.matchx("new"  ) ) return parsePostfix(P.allocations.parseNew());
        if( P.match ("{"    ) ) return parsePostfix(P.require(P.functions.parseFunction(),"}"));

        int pos = P.pos();
        // Expect an identifier now
        String id = P._lexer.matchId();
        if( id == null || Parser.KEYWORDS.contains(id) ) {
            P.pos(pos);
            throw P.errorSyntax("an identifier or expression");
        }
        int pos2 = P.pos();
        return P.references.parseName(id,pre,pos,pos2);
    }

    // Check for assign-update, "x += e0".
    // Returns "x + e0"; the caller assigns value.
    // if ch is postfix, then lhs is kept and caller will unkeep and return it.
    Node opAssign( char ch, Node lhs, Type t ) {
        // RHS of the update.
        lhs.keep();             // Alive across parseAssignment
        Node rhs =
            (byte)ch ==  1 ? P.con( 1) : // var++
            (byte)ch == -1 ? P.con(-1) : // var--
            parseAssignment();               // var op= rhs
        if( !Lexer.postfix(ch) ) lhs.unkeep();   // Allow to die in next peep
        // 4 cases:
        // int + int ==>> narrow int
        // int + flt ==>> error, caller must fail assigning flt into int
        // flt + int ==>> use float op, wrap toFloat()
        // flt + flt ==>> use float op
        Node op = switch(ch) {
        case 1, (char)-1,
             '+'        -> new AddNode(lhs,rhs);
        case '-'        -> new SubNode(lhs,rhs);
        case '*'        -> new MulNode(lhs,rhs);
        case '/'        -> new DivNode(lhs,rhs);
        case '|'        -> new  OrNode(null,lhs,rhs);
        case '&'        -> new AndNode(null,lhs,rhs);
        case '^'        -> new XorNode(P.loc(),lhs,rhs);
        case Lexer.SHL  -> new ShlNode(P.loc(),lhs,rhs);
        case Lexer.SAR  -> new SarNode(P.loc(),lhs,rhs);
        case Lexer.SHR  -> new ShrNode(P.loc(),lhs,rhs);
        default         -> throw TODO();
        };
        // Convert to float ops, or narrow int types; error if not declared type.
        // Also, if postfix LHS is still keep()
        return P.conversions.convertExpr(P.peep(op),t);
    }

    /**
     * Parse postfix expression; this can be a field expression, an array
     * lookup or a postfix operator like '#'
     *
     * <pre>
     *     expr ('.' FIELD)* [ = expr ]       // Field reference
     *     expr '#'                           // Postfix unary read operator
     *     expr ['++' | '--' ]                // Postfix unary write operator
     *     expr ('[' expr ']')* = [ = expr ]  // Array reference
     *     expr '(' [args,]* ')'              // Function call
     * </pre>
     */
    Node parsePostfixMethod(Node expr, Node self) {
        // TODO: This is too broad because the parser lacks strong types here.
        // Some normal functions can end up with a "self" argument.

        // Self method call?  Self is a TMP-not-CLZ, then assume and pass self.
        if( P.match("(") ) {
            expr = parsePostfix(P.require(P.functions.functionCall(expr,self),")"));
        } else if( expr != self )
            self.isKill();      // Not a method call, no need for self
        return parsePostfix(expr);
    }
    Node parsePostfix(Node expr) {
        P._lexer.skipWhiteSpace();
        Lexer site = P.loc();
        String name;
        if( P.match(".") ) {
            P._lexer.skipWhiteSpace();
            site=P.loc();
            name = P.requireId(true);
        }
        else if( P.match("#") ) name = "#";
        else if( P.match("[") ) name = "[]";
        // Can get here without an explicit receiver: "f()" or
        // "self.method()(next)(next)(next)".  Pass the current function self.
        else if( P.match("(") ) return parsePostfix(P.require(P.functions.functionCall(expr,P.references.defaultSelf()),")"));
        else return expr;       // No postfix

        return P.references.parseField(expr,name,0,site);
    }

    /**
     * Parse integer literal
     *
     * <pre>
     *     integerLiteral: [1-9][0-9]* | 0[0-7]* | 0[xX][0-9a-fA-F]+
     *     floatLiteral: [digits].[digits]?[e [digits]]?[f]?
     * </pre>
     */
    private ConstantNode parseLiteral() { return P.con(P._lexer.parseNumber()); }

    // Already parsed "'"
    Node parseChar() {
        return P.con(TypeInteger.constant(P._lexer.parseChar()));
    }
}
