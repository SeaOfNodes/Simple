package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.parser.Parser.ParseException;
import com.seaofnodes.simple.type.*;

import static com.seaofnodes.simple.util.Utils.TODO;

/** Shared guarded branch/merge construction for if, ?:, && and ||. */
final class ControlFlow {
    private final Parser P;

    ControlFlow(Parser P) { this.P = P; }

    // Parse a conditional expression, merging results.
    Node conditional( Node pred, String fside ) {
        // IfNode takes current control and predicate
        pred.keep();
        Node ifNode = new IfNode(P.ctrl(), pred).peephole();
        // Setup projection nodes
        CFGNode ifT = new CProjNode(ifNode.  keep(), 0, "True" ).peephole().keep();
        CFGNode ifF = new CProjNode(ifNode.unkeep(), 1, "False").peephole().keep();
        // In if true branch, the ifT proj node becomes the ctrl
        // But first clone the scope and set it as current
        ScopeNode fScope = P._scope.dup(); // Duplicate current scope
        fScope.ctrl(ifF);       // The pending arm already starts at False
        P._xScopes.push(fScope); // For graph visualization we need all scopes

        // Parse the true side
        P.ctrl(ifT.unkeep());     // set ctrl token to ifTrue projection
        P._scope.addGuards(ifT,pred,false); // Up-cast predicate
        // Parse true-side flavor
        Node lhs = switch( fside ) {
        case "else" -> P.statements.parseStatement(); // if( pred )  stmts;
        case ":"    -> P.expressions.parseAssignment();      //     pred ?  asgn;
        case "&&"   -> P.expressions.parseLogical();   //     pred && expr
        case "||"   -> pred;             //     pred || expr_is_ignored
        default     -> throw TODO();
        };
        lhs.keep();
        P._scope.removeGuards(ifT);

        // See if a one-sided def was made: "if(pred) int x = 1;" and throw.
        // See if any forward-refs were made, and copy them to the other side:
        // "pred ? n*fact(n-1) : 1"
        fScope.balanceIf(P._scope);

        ScopeNode tScope = P._scope;

        // Parse the false side
        P._scope = fScope;        // Restore scope, then parse else block if any
        ifF.unkeep();           // fScope already owns the false control
        // Up-cast predicate, even if not else clause, because predicate can
        // remain true if the true clause exits: `if( !ptr ) return 0; return ptr.fld;`
        P._scope.addGuards(ifF,pred,true);
        // Parse false-side flavor
        boolean doRHS = false;  // RHS is optional for if/else and trinary
        Node rhs = switch( fside ) {
        case "else" -> (doRHS = P.match(fside)) ? P.statements.parseStatement() : P.con(lhs._type.makeZero());
        case ":"    -> (doRHS = P.match(fside)) ? P.expressions.parseAssignment()      : P.con(lhs._type.makeZero());
        case "&&"   -> P.con(lhs._type.makeZero()); // pred && expr_is_ignored
        case "||"   -> P.expressions.parseLogical();            // pred || expr
        default     -> throw TODO();
        };
        rhs.keep();
        P._scope.removeGuards(ifF);
        // The RHS of `||` is parsed on the false side, so retain its updated
        // control, memory and locals just as an explicit else arm does.
        if( doRHS || fside.equals("||") )
            fScope = P._scope;
        pred.unkeep();

        // Check the trinary widening int/flt
        if( !fside.equals("else") ) {
            rhs = P.conversions.widenInt( rhs.unkeep(), lhs._type ).keep();
            lhs = P.conversions.widenInt( lhs.unkeep(), rhs._type ).keep();
        }

        P._scope = tScope;
        P._xScopes.pop();       // Discard pushed from graph display
        // See if a one-sided def was made: "if(pred) int x = 1;" and throw.
        // See if any forward-refs were made, and copy them to the other side:
        // "pred ? n*fact(n-1) : 1"
        tScope.balanceIf(fScope);

        // Merge results
        RegionNode r = P.ctrl(tScope.mergeScopes(fScope,P.loc()));
        Node ret = P.peep(new PhiNode("",r,lhs.unkeep(),rhs.unkeep()));
        // Immediately fail e.g. `arg ? 7 : ptr`
        ParseException err;
        if( !fside.equals("else") && (err = ret.err()) !=null ) throw err;
        ret.keep();
        r.peephole();
        return ret.unkeep();
    }
}
