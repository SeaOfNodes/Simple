package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.parser.Parser;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParserTest {
    @Test public void testDeferredFragments() {
        // The for update is scanned before the body, then parsed after it.
        // Delimiters in strings, characters and comments must not end the scan.
        String[] updates = {"n=n+\") (\"#", "n=n+')'", "n=n+/* ) */2", "n=n+{ -> 2; }()"};
        String[] results = {"6", "82", "4", "4"};
        for( int i = 0; i<updates.length; i++ ) {
            CodeGen code = new CodeGen("int n=0; for(int i=0; i<2; "+updates[i]+") i++; return n;")
                .driver(CodeGen.Phase.TypeCheck);
            assertEquals(updates[i],results[i],Eval2.eval(code,0));
        }
        // Declared constructor bodies use the same scanner with brace nesting.
        CodeGen code = new CodeGen("struct Box { int x; new Box={ -> x=\"}\"#; }; }; return new Box().x;")
            .driver(CodeGen.Phase.TypeCheck);
        assertEquals("1",Eval2.eval(code,0));
    }

    @Test public void testUnclosedDeferredFragment() {
        try {
            new CodeGen("for(int i=0; i<2; i++").driver(CodeGen.Phase.TypeCheck);
            fail("Accepted an unterminated for update");
        } catch( Parser.ParseException e ) {
            assertEquals("Unclosed for update",e.getMessage());
        }
    }
}
