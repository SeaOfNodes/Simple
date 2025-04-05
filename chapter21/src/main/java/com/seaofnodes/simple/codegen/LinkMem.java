package com.seaofnodes.simple.codegen;

// In-memory linking
public class LinkMem {
    final CodeGen _code;
    LinkMem( CodeGen code ) { _code = code; }

    public CodeGen link() {
        // Write any large constants into a constant pool; they
        // are accessed by RIP-relative addressing.
        _code._encoding.writeConstantPool(_code._encoding._bits,true);

        return _code;
    }
}
