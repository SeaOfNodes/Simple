package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import java.util.Arrays;

// Comnpute field layouts only after SCCP and its peepholes converge.
// Keep surviving functions rooted without introducing unknown callers.
abstract class FinalizeIR {
    static void run(CodeGen code) {
        // Compute field layouts and replace symbolic field offsets by constants.
        for( int i=0; i<code._start.nOuts(); i++ ) {
            Node use = code._start.out(i);
            if( use instanceof ConFldOffNode off ) {
                Node con = off.asOffset();
                assert con != null : off._ts._name+"."+off._fname;
                off.subsume(con);
                i--;
            }
        }
        code._iter.iterate(code); // Propagate field offsets and clean up.

        // Retain surviving functions for emission without adding unknown callers.
        for( FunNode fun : code._linker )
            if( fun != null ) {
                // A folded static function field can leave a detached body
                // with internal cycles. It has no callers; later phases must
                // not revive it merely because its nodes are still allocated.
                if( fun.isDead() || fun._type.isHigh() ) {
                    code._linker.set(fun.sig().fidx(),null);
                    continue;
                }
                code.keepInCompUnit(fun);
            }
        for( CompUnit cu : code._compunits.values() ) {
            ProjNode mem = cu._start.proj(1);
            if( mem != null ) code.add(mem.unkeep());
        }
        // Stable print order: initializers first, then other Returns by NID.
        for( Node stop : code._stop._inputs )
            Arrays.sort(stop._inputs._es,0,stop.nIns(),
                        (x,y) -> ((((ReturnNode)x).fun().isClz() ? -1 : x._nid) -
                                  (((ReturnNode)y).fun().isClz() ? -1 : y._nid) ));
    }
}
