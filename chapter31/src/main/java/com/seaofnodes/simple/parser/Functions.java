package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.ScopeNode.Kind;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import java.util.BitSet;

import static com.seaofnodes.simple.util.Utils.TODO;

/** Function expressions, parameters, calls, and function entry/exit graphs. */
final class Functions {
    private final Parser P;
    // Merge all function exits here.
    ScopeNode _returnScope;

    Functions(Parser P) { this.P = P; }

    /**
     * Parse a function body; the caller will parse the surrounding "{}"
     *
     * <pre>
     *     { [type arg,]* -> expr }
     *     { -> statements } // The no-argument function
     * </pre>
     */
    Node parseFunction() {
        Ary<Type> ts = new Ary<>(Type.class);
        Ary<String> ids = new Ary<>(String.class);
        BitSet finals = new BitSet();
        Type self = P.types.implicitSelfType();
        ts.push(self);
        ids.push(self == TypePtr.PTR ? "#self" : "self");

        // Parse other arguments
        P._lexer.skipWhiteSpace();
        Lexer loc = P.loc();      // First argument location
        parameters(ts,ids,finals,false);
        // Make a concrete function type, with a fidx
        TypeFunPtr tfp = TypeFunPtr.make1((byte)2,true,ts.asAry(),Type.BOTTOM,P._code.fidx(P._ref._cname));
        ReturnNode ret = parseFunctionBody(tfp,loc,finals,ids.asAry());
        return new FunPtrNode(tfp,P._code._start,ret).peephole();
    }

    Node parseConstructor(String name, boolean explicit) {
        P.require("{");
        Ary<Type> ts = new Ary<>(Type.class);
        Ary<String> ids = new Ary<>(String.class);
        BitSet finals = new BitSet();
        TypeStruct self = P._scope.constructorSelf();
        assert self != null;
        ts.push(TypeMemPtr.make(Structs.constructorRecv(self)).withPartial(true));
        ids.push("self");
        // Explicit constructors use the same hidden allocation arguments as
        // <init>: public memory is the call memory edge (index 1), followed by
        // self (index 2) and the private, unescaped object memory (index 3).
        ts.push(TypeMem.makePrivate(Type.BOTTOM));
        ids.push("#selfMem");

        P._lexer.skipWhiteSpace();
        Lexer loc = P.loc();
        parameters(ts,ids,finals,explicit);
        TypeFunPtr tfp = TypeFunPtr.make1((byte)2,true,ts.asAry(),TypeMem.makePrivate(Type.BOTTOM),P._code.fidx(P._ref._cname));
        Node[] preParse = P._scope.save();
        ReturnNode ret = parseBody((self._name+"."+name).intern(),tfp,loc,finals,ids.asAry());
        P._scope.restore(preParse);
        P.require("}");
        return new FunPtrNode(tfp,P._code._start,ret).peephole();
    }

    // parameterList followed by the function arrow; callers seed hidden parameters.
    private void parameters(Ary<Type> ts, Ary<String> ids, BitSet finals, boolean constructor) {
        while( true ) {
            Type t = P.types.parseType();    // Arg type
            if( t==null ) break;
            P._lexer.skipWhiteSpace();
            Lexer qualifier=P.loc();
            if( P.match("^") ) {
                if( !(t instanceof TypeMemPtr ptr) || ptr._obj.isAry() )
                    throw Parser.error("'^' requires a struct parameter",qualifier);
                t = ptr.withPartial(true);
                P.construction._borrowed = true;
            }
            finals.set(ids.size(),P.declarations.bindingFinal(t));
            P._lexer.skipWhiteSpace();
            Lexer nameLoc=P.loc();
            String id = P.requireId();
            if( ids.find(id)!=-1 ) throw Parser.error("Duplicate parameter '"+id+"'",nameLoc);
            ts .push(t );       // Push type/arg pairs
            ids.push(id);
            P.match(",");
        }
        P.require("->");
    }

    /**
     *  Parse function call arguments; caller will parse the surrounding "()"
     * <pre>
     *   ( arg* )
     * </pre>
     */
    Node functionCall(Node fcn, Node self) {
        Lexer site = P.loc();
        site._position--; // Opening '('; arguments can contain nested calls.
        if( fcn._type == Type.NIL )
            throw Parser.error("Calling a null function pointer",site);
        fcn.keep();            // Keep while parsing args

        Ary<Node> args = new Ary<>( Node.class );
        args.push(null);        // Space for ctrl,mem
        args.push(null);
        if( self != null )      // Method call has a self
            args.push(self.keep());
        arguments(args);
        // Control & memory after parsing args
        args.set(0,P.ctrl().keep());
        args.set(1,P.mem().keep());
        args.push(fcn);        // Function pointer
        // Unkeep them all
        for( Node arg : args )
            arg.unkeep();
        return functionCall(args,site);
    }

    Node functionCall( Ary<Node> args ) {
        return functionCall(args,P.loc());
    }

    Node functionCall( Ary<Node> args, Lexer site ) {
        // Dead into the call?  Skip all the node gen
        if( P.ctrl()._type == Type.XCONTROL ) {
            for( Node arg : args )
                if( arg.isUnused() )
                    arg.kill();
            return P.con(Type.TOP);
        }

        // Into the call
        args.set(1,P.construction.callMemory(args,args.at(1)));
        CallNode call = (CallNode)new CallNode(site, args.asAry()).peephole();
        // Post-call setup
        CallEndNode cend = (CallEndNode)new CallEndNode(call,TypeRPC.constant(P._code.rpc(P._ref._cname))).peephole();
        call.peephole();        // Rerun peeps after CallEnd, allows early inlining
        cend = (CallEndNode)cend.keep().peephole(); // TODO: Might inline
        // Control from CallEnd
        P.ctrl(new CProjNode(cend,0,ScopeNode.CTRL).peephole());
        // Memory from CallEnd
        Node mem = new ProjNode(cend,1,ScopeNode.MEM0).peephole();
        P.mem(mem);
        // Call result
        return new ProjNode(cend.unkeep(),2,"#2").peephole();
    }

    // argumentList; returns kept values and leaves the closing parenthesis to the caller.
    void arguments(Ary<Node> args) {
        while( !P.peek(')') ) {
            Node arg = P.expressions.parseAssignment();
            if( arg==null ) break;
            args.push(arg.keep());
            if( !P.match(",") ) break;
        }
    }

    /**
     *  Parses a function body, assuming the header is parsed.
     */

    private ReturnNode parseFunctionBody( TypeFunPtr sig, Lexer loc, BitSet finals, String[] ids) {
        // Record & restore the existing scope vars.  The function parse might
        // update fields, so when the parse is done reset back to the pre-parse
        // state.
        Node[] preParse = P._scope.save();

        // Parse function body normally
        ReturnNode ret = parseBody(null, sig,loc,finals,ids);

        // Reset all fields to pre-parse days
        P._scope.restore(preParse);
        return ret;
    }

    // Parse a function body

    // Stack the {break,continue,return} scopes and a new lexical scope.
    // Build a FunNode and parameters, extending scope by parm names.
    // Parse the function body
    // - while( not end-of-function )
    // - - parseStatement
    // - - If isInit, also incrementally extend the self-type.
    // If isInit
    // - upgrade and close the self-type
    // - Store class singleton fields; instance Stores already exist
    // - <init> and user constructors return their current private memory
    // Unstack the {break,continue,return} scopes.

    ReturnNode parseBody( String funName, TypeFunPtr sig, Lexer loc, String[] ids) {
        return parseBody(funName, sig, loc, null, ids);
    }

    ReturnNode parseBody( String funName, TypeFunPtr sig, Lexer loc, BitSet finals, String[] ids) {
        ArrayInitializers.Frame outerArray = P.arrays._frame;
        P.arrays._frame = null;
        Structs.Constructor outerCtor = P.structs._pendingCtor;
        P.structs._pendingCtor = null;
        boolean isInit = FunNode.isInit(funName);
        boolean isCtor = isInit || P.structs.isExplicitConstructorName(funName);
        String typeName = isCtor ? ((TypeMemPtr)sig._sig[0])._obj._name : null;

        // Stack parser state on the local Java stack, and unstack it later
        P._scope.push(new Kind.Func(funName,isCtor && !funName.endsWith(".<clinit>")));
        ScopeNode    breakScope =    P.statements._breakScope;    P.statements._breakScope = null;
        ScopeNode continueScope = P.statements._continueScope; P.statements._continueScope = null;
        ScopeNode   returnScope =   _returnScope;   _returnScope = null;

        int oldUID = P._code.UID(); // Used to approximate function size
        FunNode fun = enterFunction(funName,sig,loc,finals,ids);
        // Guards belong to this function's control flow. Do not inherit an
        // enclosing function's predicates or leak this body's guards on exit.
        P._scope.addGuards(fun,null,false);
        Construction.Frame outerConstruction = P.construction.enter(fun,isCtor && !fun.isClz());
        Node rpc = fun.parm(0);
        if( fun.isClz() ) P.structs.beginClassInit(fun,loc);

        // Parse the function body.
        Node last = P._code.ZERO;   // Last statement as the default result

        int nvar = 0;
        while (!P.peek('}') && !P._lexer.isEOF()) {
            // Parse a statement; record last statement as default return
            last = P._code.add(P.statements.parseStatement());
            // For <init> and <clinit>, local vars are really struct fields and
            // get exposed as discovered.  This allows late local functions to
            // use early declared fields.
            if( isInit )
                nvar = P.structs.updateSelfAsFieldsDiscovered( typeName, nvar );
        }

        if( isInit ) P.structs.finishConstructor();
        P.structs._pendingCtor = outerCtor;

        ParmNode self = fun.parm(2);
        // Last expression is the return
        if( P.ctrl()._type==Type.CONTROL ) {
            // Constructors get special return types
            if( fun.isClz() ) {
                assert last!=null;
            } else if( isCtor ) {
                last = P.construction.selfMem();
            }
            addReturn(last);
            // exit path is now dead (nothing live after a "return")
        }
        if( isInit )
            P.structs.finishInitializer(typeName,fun.isClz(),self);

        // Build a return from the _returnScope.
        // Can be no returns for never-exit functions
        Node rctl = _returnScope==null ? P._code.XCTRL      : _returnScope.ctrl().peephole();
        Node rmem = rctl==P._code.XCTRL  ? P.con(TypeMem.TOP) : _returnScope.mem();
        Node expr = rctl==P._code.XCTRL  ? P.con(Type.TOP)    : _returnScope._inputs.last().peephole();
        ReturnNode ret = (ReturnNode)P.peep(new ReturnNode(rctl, rmem, expr, rpc, fun));
        fun.setRet(ret);
        // Approximate function size, for inlining heuristics
        fun._approxUIDs = P._code.UID() - oldUID;
        for( Node cend : ret.outs() )
            if( cend instanceof CallEndNode )
                throw TODO(); //_code.add(cend); // have call ends ALREADY? just barely made the Return

        // Export (and keep-alive) functions until prove dead
        P._ref.addFun(P._code,fun);

        // Unstack parser state
        P._scope.removeGuards(fun);
        if( _returnScope != null ) _returnScope.kill();
        _returnScope   =   returnScope;
        P.statements._continueScope = continueScope;
        P.statements._breakScope    =    breakScope;
        P._scope.pop();
        P.construction._frame = outerConstruction;
        P.arrays._frame = outerArray;

        return ret;
    }

    private FunNode enterFunction(String funName, TypeFunPtr sig, Lexer loc, BitSet finals, String[] ids) {
        FunNode fun = (FunNode)P.peep(new FunNode(P.loc(),sig, funName, P._ref, null, P._ref._start));
        // Once the function header is available, install in linker table -
        // allowing recursive functions.  Linker matches on declared args and
        // exact fidx, and ignores the return (because the fidx will only match
        // the exact single function).
        P._code.link(fun);

        new ParmNode("$rpc",0,TypeRPC.BOT,fun,ConstantNode.seed(TypeRPC.BOT).peephole()).peephole();

        // Pre-call the function from Start, with worse-case arguments.  This
        // represents all the future, yet-to-be-parsed functions calls and
        // external calls.
        P._scope.ctrl(fun);              // Scope control from function
        // Private mem alias tracking per function
        Node defaultMem = new ProjNode(P._ref._start,1,ScopeNode.MEM0).peephole();
        Node privMem = new ParmNode(ScopeNode.MEM0,1,TypeMem.BOT,fun,defaultMem).peephole();
        P.mem(privMem);
        // All args, "as-if" called externally
        for( int i = 0; i<ids.length; i++ ) {
            Type t = sig.arg(i);
            // Args take a default input if called from Start/external-world
            Node parm = new ParmNode(ids[i],i+2,t,fun,ConstantNode.seed(fun.defaultArg(i)).peephole()).peephole();
            if( t instanceof TypeMemPtr ptr && ptr._partial ) parm = new ConstructNode(parm).peephole();
            P._scope.define(ids[i], t, (i==0 && ids[0]=="self") || (finals!=null && finals.get(i)), parm, loc);
        }

        return fun;
    }
    Node addReturn(Node expr) {
        if( P.construction._frame._owner ) expr = P.construction.selfMem();
        expr.keep();
        // Force the pending lazy bulk-memory Phi.
        P.mem();                 // Force a pending lazy bulk-memory Phi
        // Class fields are still stored at exit. Instance fields already
        // travel in expr's private memory; only their scope layout is retained.
        int lexN = P._scope.enclosingFuncOrDecl();
        Kind.Func k = (Kind.Func)P._scope._kinds.at(lexN);
        if( FunNode.isInit(k._name) ) {
            int nestedLexSize = lexN+1 < P._scope.depth() ? P._scope._kinds.at(lexN+1)._lexSize : P._scope.nIns();
            if( k._name.endsWith(".<clinit>") )
                for( int i = k._lexSize; i<nestedLexSize; i++ )
                    P._scope.update(P._scope.var(i),null);
            if( _returnScope != null ) {
                Var vexpr = _returnScope._vars.pop();
                Node oldX = _returnScope.removeLast();
                int rlen = _returnScope.nIns();
                while( rlen  < nestedLexSize ) {
                    _returnScope._vars.add(P._scope.var(rlen));
                    _returnScope.addDef(P.con(P._scope.var(rlen++).type().makeZero()));
                }
                _returnScope._vars.add(vexpr);
                _returnScope.addDef(oldX);
            }
        }

        // No prior merge point?  Just clone and hang on to it
        if( _returnScope == null ) {
            _returnScope = P._scope.dup();
            while( lexN+1 < _returnScope.depth() )
                _returnScope._pop(); // Pop a nested block scope until we hit the function scope
            _returnScope.define("$expr", Conversions.inferredReturnType(expr._type), true, expr, null);

        } else {
            // The initializer scopes have already been aligned above.
            int rlen = _returnScope.nIns()-1;
            RegionNode r = P.ctrl(new RegionNode(null, null,_returnScope.ctrl(), P._scope.ctrl()).init().keep());
            _returnScope._merge(P._scope, r, rlen);
            Node oldExpr = _returnScope.in(rlen);
            _returnScope.setDef(rlen,PhiNode.make("$expr",expr._type, r, oldExpr, expr).peephole());
            P._code.add(r);
            _returnScope.ctrl(r.unkeep());
        }
        P.ctrl(P._code.XCTRL);      // Kill control
        return expr.unkeep();
    }
}
