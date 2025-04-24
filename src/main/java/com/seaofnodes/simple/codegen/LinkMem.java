package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.Utils;
import com.seaofnodes.simple.node.Node;

// In-memory linking
public class LinkMem {
    final CodeGen _code;
    LinkMem( CodeGen code ) { _code = code; }

    public CodeGen link() {
        Encoding enc = _code._encoding;

        // Patch external calls internally
        enc.patchGlobalRelocations();

        // Write any large constants into a constant pool; they
        // are accessed by RIP-relative addressing.
        enc.writeConstantPool(enc._bits,true);

        return _code;
    }
}
