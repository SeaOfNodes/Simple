package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.TypeStruct;
import com.seaofnodes.simple.util.Utils;

// In-memory linking
public class LinkMem {
    final CodeGen _code;
    LinkMem( CodeGen code ) { _code = code; }

    public CodeGen link() {
        Encoding enc = _code._encoding;

        // Patch external calls internally
        enc.patchGlobalRelocations();

        // Place constant pool after code, static data after constants
        int code  = 0;                     // Code start
        int cpool = (enc._bits .size() + code  + 15) & -16;  // pad to 16
        int sdata = (enc._cpool.size() + cpool + 15) & -16;


        // Patch local ops, e.g. loading float constants from the constant pool
        for( Node op : enc._bigCons.keySet() ) {
            Encoding.Relo relo = enc._bigCons.get(op);
            boolean ro = !(relo._t instanceof TypeStruct ts) || ts.isConstant();
            int target = relo._target+(ro ? cpool : sdata);
            ((RIPRelSize)relo._op).patch(enc, relo._opStart, enc._opLen[relo._op._nid], target - relo._opStart);
        }

        return _code;
    }
}
