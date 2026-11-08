package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.ScopeNode.Kind;
import com.seaofnodes.simple.type.*;

/** Blocks, statements, and loops; builds their control-flow scopes. */
final class Statements {
    private final Parser P;

    // Merge all the while-breaks    here
    ScopeNode _breakScope;
    // Merge all loop continues here.
    ScopeNode _continueScope;

    Statements(Parser P) { this.P = P; }

    /**
     * Parses a statement
     *
     * <pre>
     *     returnStatement | declStatement | blockStatement | ifStatement | expressionStatement
     * </pre>
     * @return a {@link Node} or {@code null}
     */
    Node parseStatement() {
        P._lexer.skipWhiteSpace();
        Lexer site=P.loc();
        if(      P.matchx("return")  ) return parseReturn();
        else if (P.matchx("if")      ) return parseIf();
        else if (P.matchx("while")   ) return parseWhile();
        else if (P.matchx("for")     ) return parseFor();
        else if (P.matchx("break")   ) return parseBreak(site);
        else if (P.matchx("continue")) return parseContinue(site);
        else if (P.matchx("struct")  ) return P.structs.parseStruct();
        else if (P.structs.inStructDeclaration() && P.matchx("new")) return P.structs.parseConstructorDeclaration();
        else if (P.matchx(";")       ) return P._code.ZERO; // Empty statement
        // Break ambiguity around leading function types and starting a block
        else if (P.peek('{') && !P.types.isTypeFun() ) {
            P.match("{");
            return P.require(parseBlock(new Kind.Block()),"}");
        }
        // Declaration or normal assignment/expression
        else return P.declarations.parseDeclarationStatement();
    }

    /**
     * Parses a block
     *
     * <pre>
     *     '{' statements '}'
     * </pre>
     * Does not parse the opening or closing '{}'
     * @return a {@link Node} or {@code null}
     */
    private Node parseBlock(Kind kind) {
        // Enter a new scope
        P._scope.push(kind);
        Node last = P._code.ZERO;
        while (!P.peek('}') && !P._lexer.isEOF()) {
            P._code.add(last);
            last = parseStatement();
        }
        // Exit scope
        last.keep();
        P._scope.pop();
        return last.unkeep();
    }

    /**
     * Parses a return statement; "return" already parsed.
     * The $ctrl edge is killed.
     *
     * <pre>
     *     'return' expr ;
     * </pre>
     * @return an expression {@link Node}, never {@code null}
     */
    private Node parseReturn() {
        var expr = P.require(P.expressions.parseAssignment(), ";");
        return P.functions.addReturn(expr);
    }

    /**
     * Parses a statement
     *
     * <pre>
     *     if ( expression ) statement [else statement]
     * </pre>
     * @return a {@link Node}, never {@code null}
     */
    private Node parseIf() {
        // Parse predicate
        P.require("(");
        var pred = P.require(P.expressions.parseAssignment(), ")");
        return P.controlflow.conditional(pred,"else");
    }

    /**
     * Parses a while statement
     *
     * <pre>
     *     while ( expression ) statement
     * </pre>
     * @return a {@link Node}, never {@code null}
     */
    private Node parseWhile() {
        P.require("(");
        return parseLooping(false);
    }

    /**
     * Parses a for statement
     *
     * <pre>
     *     for( var x=init; test; incr ) body
     * </pre>
     * @return a {@link Node}, never {@code null}
     */
    private Node parseFor() {
        // {   var x=init,y=init,...;
        //     while( test ) {
        //         body;
        //         next;
        //     }
        // }
        P.require("(");
        P._scope.push(new Kind.Block()); // Scope for the index variables
        if( !P.match(";") )        // Can be empty init "for(;test;next) body"
            P.declarations.parseDeclarationStatement(); // Non-empty init
        Node rez = parseLooping(true);
        P._scope.pop();           // Exit index variable scope
        return rez;
    }

    // Shared by `for` and `while`
    private Node parseLooping( boolean doFor ) {

        var savedContinueScope = _continueScope;
        var savedBreakScope    = _breakScope;

        // Loop region has two control inputs, the first is the entry
        // point, and second is back edge that is set after loop is parsed
        // (see end_loop() call below).  Note that the absence of back edge is
        // used as an indicator to switch off peepholes of the region and
        // associated phis; see {@code inProgress()}.

        P.ctrl(new LoopNode(P.loc(),null,P.ctrl()).peephole()); // Note we set back edge to null here

        // At loop head, we clone the current Scope (this includes all
        // names in every nesting level within the Scope).
        // We create phis eagerly for all the names we find, see dup().

        // Save the current scope as the loop head
        ScopeNode head = P._scope.keep();
        // Clone the head Scope to create a new Scope for the body.
        // Create phis eagerly as part of cloning
        P._xScopes.push(P._scope = P._scope.dup(true)); // The true argument triggers creating phis

        // Parse predicate
        var pred = P.peek(';') ? P.con(1) : P.expressions.parseAssignment();
        P.require( doFor ? ";" : ")" );

        // IfNode takes current control and predicate
        Node ifNode = new IfNode(P.ctrl(), pred.keep()).peephole();
        // Setup projection nodes
        CFGNode ifT = new CProjNode(ifNode.  keep(), 0, "True" ).peephole().keep();
        CFGNode ifF = (CFGNode)new CProjNode(ifNode.unkeep(), 1, "False").peephole();

        // for( ;;next ) body
        int nextPos = -1, nextEnd = -1;
        if( doFor ) {
            // Skip the next expression and parse it later
            nextPos = P.pos();
            P._lexer.skipTo('(',')',"for update");
            nextEnd = P.pos();
            P.require(")");
        }

        // Clone the body Scope to create the break/exit Scope which accounts for any
        // side effects in the predicate.  The break/exit Scope will be the final
        // scope after the loop, and its control input is the False branch of
        // the loop predicate.  Note that body Scope is still our current scope.
        P.ctrl(ifF);
        P._xScopes.push(_breakScope = P._scope.dup());
        _breakScope.addGuards(ifF,pred,true); // Up-cast predicate

        // No continues yet
        _continueScope = null;

        // Parse the true side, which corresponds to loop body
        // Our current scope is the body Scope
        P.ctrl(ifT.unkeep());     // set ctrl token to ifTrue projection
        P._scope.addGuards(ifT,pred.unkeep(),false); // Up-cast predicate
        parseStatement().isKill();                 // Parse loop body
        P._scope.removeGuards(ifT);

        // Merge the loop bottom into other continue statements
        if (_continueScope != null) {
            _continueScope = jumpTo(_continueScope);
            P._scope.kill();
            P._scope = _continueScope;
        }

        // Now append the next code onto the body code
        if( doFor ) {
            int old = P.pos(nextPos);
            if( !P.peek(')') )
              P.expressions.parseAssignment().isKill();
            if( P.pos() != nextEnd )
                throw P.errorSyntax( "Unexpected code after expression" );
            P.pos(old);
        }

        // The true branch loops back, so whatever is current _scope.ctrl gets
        // added to head loop as input.  endLoop() updates the head scope, and
        // goes through all the phis that were created earlier.  For each phi,
        // it sets the second input to the corresponding input from the back
        // edge.  If the phi is redundant, it is replaced by its sole input.
        var exit = _breakScope;
        head.balanceLoopFRefs(P._scope);
        exit.balanceLoopFRefs(P._scope);
        head.endLoop(P._scope, exit);
        head.unkeep().kill();

        P._xScopes.pop();       // Cleanup
        P._xScopes.pop();       // Cleanup

        _continueScope = savedContinueScope;
        _breakScope = savedBreakScope;

        // At exit the false control is the current control, and
        // the scope is the exit scope after the exit test.
        // During sys parsing, there is no xscope here.
        if( !P._xScopes.isEmpty() ) {
            P._xScopes.pop();
            P._xScopes.push( exit );
        }
        P._scope = exit;
        return P._code.ZERO;
    }
    private Node parseBreak(Lexer site) {
        checkLoopActive(site);
        // At the time of the break, and loop-exit conditions are only valid if
        // they are ALSO valid at the break.  It is the intersection of
        // conditions here, not the union.
        _breakScope.removeGuards(_breakScope.ctrl());
        _breakScope = P.require(jumpTo(_breakScope ),";");
        _breakScope.addGuards((CFGNode)_breakScope.ctrl(), null, false);
        return P._code.ZERO;
    }

    private Node parseContinue(Lexer site) { checkLoopActive(site); _continueScope = P.require(jumpTo( _continueScope ),";"); return P._code.ZERO; }

    private ScopeNode jumpTo(ScopeNode toScope) {
        ScopeNode cur = P._scope.dup();
        P.ctrl(P._code.XCTRL); // Kill current scope
        // Prune nested lexical scopes that have depth > than the loop head
        // We use _breakScope as a proxy for the loop head scope to obtain the depth
        // The clone shares lexical boundaries and Var records with the active
        // parser scope. Promoting on the clone would move those boundaries
        // without moving the active scope's bindings. Drop locals first, then
        // copy forward references into the jump scope with their own indices.
        while( cur.depth() > _breakScope.depth() )
            cur._pop();
        cur.balanceLoopFRefs(P._scope);
        // If this is a continue then first time the target is null
        // So we just use the pruned current scope as the base for the
        // "continue"
        if( toScope == null )
            return cur;
        // toScope is either the break scope, or a scope that was created here
        assert toScope.depth() <= _breakScope.depth();
        // A later path can discover forward references after an earlier
        // break/continue saved its scope. Keep those bindings across the merge.
        toScope.balanceLoopFRefs(cur);
        cur.balanceLoopFRefs(toScope);
        toScope.ctrl(toScope.mergeScopes(cur,P.loc()).peephole());
        return toScope;
    }

    private void checkLoopActive(Lexer site) { if (_breakScope == null) throw Parser.error("No active loop for a break or continue",site); }
}
