package com.seaofnodes.simple.codegen;

import static org.junit.Assert.*;

public class ParseAllTestSupport {
    public static void checkRequiredTypes(CodeGen code) {
        CompUnit a = code._compunits.get("App/A");
        CompUnit b = code._compunits.get("App/B");
        CompUnit c = code._compunits.get("App/C");
        assertNotNull(b);
        assertTrue(a._deps.find(b)!=-1);
        // C was parsed after A, while B's layout was still unresolved.
        // Only A used that layout; C must not inherit A's build dependency.
        assertNull(c._deps);
    }
}
