package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;

/** Allocation expressions and declaration-side allocation helper graphs. */
final class Allocations {
    private final Parser P;

    Allocations(Parser P) { this.P = P; }

    /**
       Parse an allocation
     */
    Node parseNew() {
        Type t = P.types.parseType();
        if( t==null ) {
            String tname = P._lexer.matchId();
            if( tname==null || Parser.KEYWORDS.contains(tname) )
                throw P.error("Expected a type");
            t = P.types.forwardRefType(tname);
        }
        // Parse ary[ length_expr ]
        if( P.match("[") ) {
            if( !t.makeZero().isa(t) )
                throw P.error("Cannot allocate a non-nullable, since arrays are always zero/null filled");
            Node len = P.expressions.parseAssignment().keep();
            P.require("]");
            return allocArray(P.types.typeAry(t,false),len);
        } else {
            if( !(t instanceof TypeMemPtr tmp) )
                throw P.error("Cannot allocate a "+t.str());
            return allocStruct(tmp._obj);
        }
    }

    private Node allocStruct(TypeStruct ts) {
        Ary<Node> ctorArgs = constructorArgs();
        // A field declaration can leave a short forward type name in TYPES.
        // Allocation happens in lexical context, so resolve that short name
        // to the completed nested type before selecting its class helper.
        String typeName = ts._name.indexOf('.') == -1 ? P.types.fullTypeName(ts._name) : ts._name;
        Type resolved = Parser.TYPES.get(typeName);
        if( resolved instanceof TypeStruct rts ) ts = rts;
        TypeStruct clz = (TypeStruct)Parser.TYPES.get(TypeStruct.addClzPrefix(typeName));
        if( clz==null )
            throw P.error("Unknown struct type '" + ts._name + "'");
        Field afld = clz.field("<new>");
        Node alloc;
        if( afld != null ) {
            TypeFunPtr tfp = (TypeFunPtr)afld._t;
            FunNode fun = P._code.link(tfp);
            alloc = fun==null
                ? new FRefNode((TypeStruct.addClzPrefix(typeName)+".<new>").intern(),P.loc())
                : new FunPtrNode(tfp,P._code._start,fun.ret()).peephole();
        } else {
            if( !ts._fref )
                throw P.error("Constructor arguments for '" + ts._name + "' but no constructor is defined");
            Type[] sig = new Type[ctorArgs._len];
            for( int i = 0; i<ctorArgs._len; i++ )
                sig[i] = ctorArgs.at(i)._type;
            alloc = new FRefNode((TypeStruct.addClzPrefix(typeName)+".<new>").intern(),P.loc(),
                                 TypeFunPtr.make(false,sig,TypeMemPtr.make(ts)));
        }
        Ary<Node> args = new Ary<>(Node.class);
        args.add(P.ctrl()); args.add(P.mem());
        for( Node arg : ctorArgs ) args.add(arg.unkeep());
        args.add(alloc);
        return P.functions.functionCall(args);
    }

    private Ary<Node> constructorArgs() {
        Ary<Node> args = new Ary<>(Node.class);
        if( !P.match("(") )
            return args;
        P.functions.arguments(args);
        P.require(")");
        return args;
    }

    private Node allocArray(TypeStruct ts, Node len) {
        Field  lenFld = ts.field("#" );
        Field bodyFld = ts.field("[]");

        ConFldOffNode base = ConFldOffNode.make(ts, "[]");
        int scale = ts.aryScale();
        Node size = P.peep(new AddNode(base,P.peep(new ShlNode(null,len,P.con(scale)))));

        // Build a NewNode; takes in and puts out all aliases.
        NewNode nnn = new NewNode(ts, P.ctrl(), size ).init();
        ProjNode self = new ProjNode(nnn,0,ts.str()).init().keep();
        ProjNode smem = new ProjNode(nnn,1,"#selfMem").init().keep();

        // Store length.  Rest of array is zero'd via CALLOC during CodeGen.
        // Length is casted to sanity.
        // TODO: Needs runtime check.
        Node st = P.peep(new StoreNode(null, "#", 1, TypeInteger.U32, null, smem, self, ConFldOffNode.make(ts,"#"), len.unkeep(), true));

        // TODO: Allow add-on "constructor" to init the array elements

        Node prior = P.mem();
        Node esc = P.peep(new EscapeNode(lenFld,self,st,prior));
        P.mem(P.mergeAlias(prior,lenFld._alias,esc));
        prior = P.mem();
        esc = P.peep(new EscapeNode(bodyFld,self,smem,prior));
        P.mem(P.mergeAlias(prior,bodyFld._alias,esc));
        smem.unkeep();
        return self.unkeep();
    }

    Node newString(String s) {
        TypeStruct ts = P.types.typeAry(TypeInteger.U8,true);
        int  lenAlias = ts.field("#" )._alias;
        int elemAlias = ts.field("[]")._alias;
        TypeConAryB body = TypeConAryB.make(s);
        TypeInteger slen = TypeInteger.constant(s.length());
        // Make a TMP, not-null (byte)2, singleton (true) (requires text
        // strings are hash-interned), with a TypeStruct having a constant
        // array body.
        TypeMemPtr str = TypeMemPtr.make((byte)2,TypeStruct.makeAry("[]u8", slen, lenAlias, body, elemAlias, true),true);
        assert str.isConstant();
        return P.con(str.makeRO());
    }

    // Build the declaration-side allocation helper.  Allocation sites only
    // call this function; all layout, initialization and publication choices
    // are made here, where the complete struct and constructor are known.
    TypeFunPtr makeAllocator(TypeStruct ts, ReturnNode initRet, Field ctorFld) {
        TypeFunPtr ctor = ctorFld==null ? null : (TypeFunPtr)ctorFld._t;
        int nargs = ctor==null ? 0 : ctor.nargs()-2; // Hide self and selfMem
        Type[] atypes = new Type[nargs];
        for( int i = 0; i<nargs; i++ ) atypes[i] = ctor.arg(i+2);
        TypeMemPtr ptr = TypeMemPtr.make(ts);
        TypeFunPtr sig = TypeFunPtr.make1((byte)2,true,atypes,ptr,P._code.fidx(P._ref._cname));
        String name = (ts._name+".<new>").intern();

        int oldUID = P._code.UID();
        FunNode fun = (FunNode)new FunNode(P.loc(),sig,name,P._ref,null,P._ref._start).peephole();
        P._code.link(fun);
        Node rpc = new ParmNode("$rpc",0,TypeRPC.BOT,fun,ConstantNode.seed(TypeRPC.BOT).peephole()).peephole();
        Node pub = new ParmNode(ScopeNode.MEM0,1,TypeMem.BOT,fun,
                                new ProjNode(P._ref._start,1,ScopeNode.MEM0).peephole()).peephole();
        Node[] parms = new Node[nargs];
        for( int i = 0; i<nargs; i++ )
            parms[i] = new ParmNode("arg"+i,i+2,atypes[i],fun,ConstantNode.seed(atypes[i]).peephole()).peephole();

        NewNode nnn = new NewNode(ts,fun,ConFldOffNode.make(ts," len")).init();
        Node self = new ProjNode(nnn,0,ts.str()).init();
        Node priv = new ProjNode(nnn,1,"#selfMem").init();
        Node[] initArgs = new Node[]{fun,pub,self,priv,
            new FunPtrNode(initRet.fun().sig(),P._code._start,initRet).peephole()};
        CallEndNode cend = rawCall(initArgs,initRet.fun());
        Node ctl = new CProjNode(cend,0,ScopeNode.CTRL).peephole();
        pub = new ProjNode(cend,1,ScopeNode.MEM0).peephole();
        priv = new ProjNode(cend,2,"#selfMem").peephole();

        if( ctor != null ) {
            Node[] cargs = new Node[nargs+5];
            cargs[0] = ctl; cargs[1] = pub; cargs[2] = self; cargs[3] = priv;
            for( int i = 0; i<nargs; i++ ) cargs[i+4] = parms[i];
            FunNode cfun = P._code.link(ctor);
            cargs[cargs.length-1] = new FunPtrNode(ctor,P._code._start,cfun.ret()).peephole();
            cend = rawCall(cargs,cfun);
            ctl = new CProjNode(cend,0,ScopeNode.CTRL).peephole();
            pub = new ProjNode(cend,1,ScopeNode.MEM0).peephole();
            priv = new ProjNode(cend,2,"#selfMem").peephole();
        }

        priv.keep();
        for( Field field : ts._fields ) {
            Type storage = field._t.makeStorage();
            Field escaped = field._final ? field : field.makeFrom(storage).makeFrom(true);
            pub.keep();
            Node esc = new EscapeNode(escaped,self,priv,pub).peephole();
            MemMergeNode merge = new MemMergeNode(null,pub);
            merge.alias(field._alias,esc);
            Node next = merge.peephole();
            pub.unkeep();
            pub = next;
        }
        priv.unkill();
        ReturnNode ret = (ReturnNode)new ReturnNode(ctl,pub,self,rpc,fun).peephole();
        fun.setRet(ret);
        fun._approxUIDs = P._code.UID() - oldUID;
        P._ref.addFun(P._code,fun);
        return sig;
    }

    private CallEndNode rawCall(Node[] args, FunNode target) {
        // Establish this known declaration-side edge immediately, but do not
        // peephole or inline it while the surrounding factory is being built.
        // IterPeeps therefore starts with the complete initializer caller set.
        CallNode call = new CallNode(P.loc(),args).init();
        CallEndNode cend = new CallEndNode(call,TypeRPC.constant(P._code.rpc(P._ref._cname))).init();
        call.link(target);
        return cend;
    }
}
