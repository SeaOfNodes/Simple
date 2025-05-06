package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Parser;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.Arrays;

// Finish the ideal graph before type checking and scheduling.
abstract class FinalizeIR {
    static void run(CodeGen code) {
        // Not really a true optimistic pass, but look for unlinked functions.
        // This can be removed, which may trigger another round of pessimistic.
        // This is the point where we flip from a virtual Call Graph (any call
        // can call any function) to having a correct (but conservative) CG.
        int progress = 0;
        while( progress != code._start.nOuts() ) {
            progress = code._start.nOuts();
            FunNode main = code.link(code._main);
            for( int i=0; i<code._start.nOuts(); i++ ) {
                Node use = code._start.out(i);
                if( use instanceof FunNode fun &&
                    fun.nIns()==2 && fun.in(1)==code._start && fun != main && !fun.hasPtr() &&
                    (fun._name==null || fun._name.startsWith("sys.")) ) {
                    code.add(fun).setDef(1,Parser.XCTRL);
                    code.addAll(fun._outputs);
                    i--;
                }
            }
            code._iter.iterate(code);
        }

        // Freeze field sizes; do struct layouts; convert field offsets into
        // constants.
        for( int i=0; i<code._start.nOuts(); i++ ) {
            Node use = code._start.out(i);
            if( use instanceof ConFldOffNode off ) {
                TypeMemPtr tmp = (TypeMemPtr) Parser.TYPES.get(off._name);
                off.subsume( off.asOffset(tmp._obj) );
                i--;
            }
        }
        code._iter.iterate(code);

        // To help with testing, sort StopNode returns by NID
        Arrays.sort(code._stop._inputs._es,0,code._stop.nIns(),(x,y) -> x._nid - y._nid );

    }
}
