package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;

// The small, eagerly readable part of a function's serialized implementation.
record FunctionSummary(TypeFunPtr sig, TypeTuple state, String name, int size, int[] classDependencyFIDXs) {
    void install(CodeGen code, CompUnit cu) {
        FunNode fun = new FunNode(null,sig,name,cu,null,cu._start);
        fun._approxUIDs=size;
        fun.init();
        // First user stays in output slot zero until the summary is removed.
        SummaryNode body = new SummaryNode(state,classDependencyFIDXs,fun);
        ParmNode rpc = new ParmNode("$rpc",0,TypeRPC.BOT,fun,code.con(TypeRPC.BOT)).init();
        ParmNode mem = new ParmNode("$mem",1,TypeMem.BOT,fun,cu._start.proj(1)).init();
        body.addDef(mem);
        for( int i = 0; i<sig.nargs(); i++ )
            body.addDef(new ParmNode("arg"+i,i+2,sig.arg(i),fun,code.con(sig.arg(i))).init());
        body.init();
        Node ctrl = new CProjNode(body,0,"ctrl").init();
        Node memory = new ProjNode(body,1,"mem").init();
        Node value = new ProjNode(body,2,"value").init();
        ReturnNode ret = new ReturnNode(ctrl,memory,value,rpc,fun).init();
        fun.setRet(ret);
        cu._stop.addDef(ret);
        code.link(fun);
    }
}
