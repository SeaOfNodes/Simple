package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.ScopeNode.Kind;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;

/** Array construction keeps the destination private until all elements exist.
 * A generator is a scoped, immediately executed loop body, not an escaping closure. */
final class ArrayInitializers {
    private final Parser P;
    Frame _frame;

    static final class Frame {
        final Kind.Block _kind;
        final int _depth;
        final Var _value;
        ScopeNode _returns;
        Frame(Kind.Block kind, int depth, Var value) {
            _kind=kind; _depth=depth; _value=value;
        }
    }

    ArrayInitializers(Parser P) { this.P=P; }

    // Opening brace consumed; an absent length means a list with inferred size.
    Node parse(Type elem, boolean readonly, Node len, Lexer site) {
        int pos=P.pos();
        Type indexType=P.types.parseType();
        String index=indexType==null ? null : P._lexer.matchId();
        boolean generator=index!=null && P.match("->");
        if( !generator ) P.pos(pos);
        if( generator && Parser.KEYWORDS.contains(index) )
            throw Parser.error("Expected an array index name",site);
        if( generator && (indexType!=TypeInteger.BOT && indexType!=TypeInteger.I32 && indexType!=TypeInteger.U32) )
            throw Parser.error("Array generator index must be int, i64, or u32",site);
        if( generator && len==null )
            throw Parser.error("Array generator requires a length",site);

        Ary<Node> values=new Ary<>(Node.class);
        if( !generator ) {
            if( !P.peek('}') ) do {
                values.add(P.conversions.convertExpr(P.expressions.parseAssignment(),elem).keep());
            } while( P.match(",") && !P.peek('}') );
            P.require("}");
            if( len==null ) len=P.con(values.size()).keep();
        }

        TypeStruct ts=P.types.typeAry(elem,readonly);
        Field body=ts.field("[]"), length=ts.field("#");
        Node size=new AddNode(ConFldOffNode.make(ts,"[]"),
                             new ShlNode(null,len,P.con(ts.aryScale())).peephole()).peephole();
        NewNode alloc=new NewNode(ts,P.ctrl(),size).init();
        Node self=new ProjNode(alloc,0,ts.str()).init().keep();
        Node mem=new ProjNode(alloc,1,"#arrayMem").init().keep();
        Node slen=new StoreNode(site,"#",length._alias,TypeInteger.U32,null,mem,self,
                               ConFldOffNode.make(ts,"#"),len,true).peephole().keep();

        if( generator ) {
            mem=generator(ts,self,mem,len,index,indexType,site);
        } else {
            for( int i=0; i<values.size(); i++ ) {
                Node st=store(ts,self,mem,P.con(i),values.at(i),site).keep();
                mem.unkeep();
                mem=st;
            }
        }

        Node result=new ArrayInitNode(P.ctrl(),mem,self,len,
                                      generator ? -1 : values.size(),site,values).peephole().keep();
        Node prior=P.mem();
        P.mem(P.mergeAlias(prior,length._alias,new EscapeNode(length,result,slen.unkeep(),prior).peephole()));
        prior=P.mem();
        P.mem(P.mergeAlias(prior,body._alias,new EscapeNode(body,result,mem,prior).peephole()));
        mem.unkeep(); self.unkeep(); len.unkeep();
        for( Node value : values ) value.unkeep();
        result.unkeep();
        return readonly ? new ReadOnlyNode(result).peephole() : result;
    }

    private Node store(TypeStruct ts, Node self, Node mem, Node idx, Node value, Lexer site) {
        Field fld=ts.field("[]");
        Node off=new AddNode(ConFldOffNode.make(ts,"[]"),
                             new ShlNode(null,idx,P.con(ts.aryScale())).peephole()).peephole();
        return new StoreNode(site,"[]",fld._alias,fld._t,P.ctrl(),mem,self,off,value,true).peephole();
    }

    private Var define(String name, Type type, boolean fin, Node value, Lexer site) {
        if( !P._scope.define(name,type,fin,value,site) ) throw Parser.error("Duplicate name '"+name+"'",site);
        return P._scope.lookup(name);
    }

    private Node read(Var var) { return P._scope.in(P._scope.update(var,null)); }

    private Node generator(TypeStruct ts, Node self, Node mem, Node len, String name,
                           Type indexType, Lexer site) {
        Frame outer=_frame;
        ScopeNode oldBreak=P.statements._breakScope, oldContinue=P.statements._continueScope;
        P.statements._breakScope=P.statements._continueScope=null;
        Kind.Block kind=new Kind.Block();
        P._scope.push(kind);
        // The counter stays wide even when the source index is a narrow int.
        // The private memory and result slots cannot be named in source.
        Var counter=define("$arrayIndex",TypeInteger.BOT,false,P.con(0),site);
        Var memory=define("$arrayMem",TypeMem.makePrivate(Type.BOTTOM),false,mem.unkeep(),site);
        Var result=define("$arrayValue",ts.field("[]")._t,false,P.con(Type.TOP),site);
        _frame=new Frame(kind,P._scope.depth(),result);

        P.ctrl(new LoopNode(site,null,P.ctrl()).peephole());
        ScopeNode head=P._scope.keep();
        P._xScopes.push(P._scope=P._scope.dup(true));
        Node idx=read(counter).keep();
        Node pred=new BoolNode.LT(idx,len).peephole().keep();
        Node iff=new IfNode(P.ctrl(),pred).peephole().keep();
        CFGNode yes=new CProjNode(iff,0,"True").peephole().keep();
        CFGNode no=(CFGNode)new CProjNode(iff.unkeep(),1,"False").peephole();
        P.ctrl(no);
        ScopeNode exit=P._scope.dup();
        P._xScopes.push(exit);
        exit.addGuards(no,pred,true);
        P.ctrl(yes.unkeep());
        P._scope.addGuards(yes,pred.unkeep(),false);

        P._scope.push(new Kind.Block());
        define(name,indexType,true,new ConvertNode(indexType,idx).peephole(),site);
        Node last=P._code.ZERO;
        while( !P.peek('}') && !P._lexer.isEOF() ) {
            P._code.add(last);
            last=P.statements.parseStatement();
        }
        P.require("}");
        if( P.ctrl()._type!=Type.XCONTROL ) addReturn(last);
        P._scope.pop();
        if( _frame._returns!=null ) {
            P._scope.kill();
            P._scope=_frame._returns;
        }
        Node value=read(result);
        // The loop starts with the whole private array memory. Keep that shape
        // on the backedge too: mixing it with an element slice yields BOTTOM,
        // which would falsely publish every function when the array escapes.
        Node prior=read(memory).keep();
        Node st=store(ts,self,prior,idx,value,site);
        P._scope.update(memory,P.mergeAlias(prior.unkeep(),ts.field("[]")._alias,st));
        P._scope.update(counter,new AddNode(idx.unkeep(),P.con(1)).peephole());
        P._scope.removeGuards(yes);
        head.balanceLoopFRefs(P._scope);
        exit.balanceLoopFRefs(P._scope);
        head.endLoop(P._scope,exit);
        head.unkeep().kill();
        P._xScopes.pop();
        P._xScopes.pop();
        if( !P._xScopes.isEmpty() ) { P._xScopes.pop(); P._xScopes.push(exit); }
        P._scope=exit;
        Node done=read(memory).keep();
        P._scope.pop();
        _frame=outer;
        P.statements._breakScope=oldBreak;
        P.statements._continueScope=oldContinue;
        return done;
    }

    Node addReturn(Node expr) {
        Node value=P.conversions.convertExpr(expr,_frame._value.type());
        value=new ArrayInitNode(P.ctrl(),value,P.loc()).peephole().keep();
        P.mem(); // Materialize lazy memory before cloning a return path.
        ScopeNode cur=P._scope.dup();
        // Saved paths share Var records with the active scope. Drop locals
        // without promoting them, then copy forward references explicitly.
        while( cur.depth()>_frame._depth ) cur._pop();
        cur.balanceLoopFRefs(P._scope);
        cur.update(_frame._value,value.unkeep());
        if( _frame._returns==null ) _frame._returns=cur;
        else {
            _frame._returns.balanceLoopFRefs(cur);
            cur.balanceLoopFRefs(_frame._returns);
            _frame._returns.ctrl(_frame._returns.mergeScopes(cur,P.loc()).peephole());
        }
        P.ctrl(P._code.XCTRL);
        return P._code.ZERO;
    }

    void checkAssignment(Var var, Lexer site) {
        if( _frame!=null && var._idx<_frame._kind._lexSize )
            throw Parser.error("Cannot assign captured local '"+var._name+"' in an array generator",site);
    }
}
