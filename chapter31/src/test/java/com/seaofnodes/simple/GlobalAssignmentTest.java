package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.parser.Parser;
import org.junit.Test;
import static org.junit.Assert.*;

public class GlobalAssignmentTest {
    private static final String SOURCE = """
        int value=1;
        int writes=0;
        u8 small=0;
        val set={int n -> value=n; return value;};
        val set_noInline={int n ->
            if( n<0 ) value=3;
            else value=n;
            return value;
        };
        val rhs_noInline={ -> writes=writes+1; return value+1;};
        val shadow={int value -> value=42; return value;};
        struct Writer {
            int seen;
            new Writer={int n -> value=n; seen=value;};
        };
        struct InitWriter { int seen=(value=17); };
        val main={ ->
            if( set(5)!=5 || value!=5 ) return 1;
            if( set_noInline(-1)!=3 || value!=3 ) return 2;
            if( set_noInline(8)!=8 || value!=8 ) return 3;
            int result=(value=rhs_noInline());
            if( result!=9 || value!=9 || writes!=1 ) return 4;
            value=writes=11;
            if( value!=11 || writes!=11 ) return 5;
            if( shadow(0)!=42 || value!=11 ) return 6;
            int wide=(small=257);
            if( wide!=257 || small!=1 ) return 7;
            val constructed=new Writer(13);
            if( value!=13 || constructed.seen!=13 ) return 8;
            val initialized=new InitWriter;
            // Default expressions run at declaration time, not per instance.
            if( value!=13 || initialized.seen!=17 ) return 9;
            return 0;
        };
        return main();
        """;

    @Test public void storesGlobalMemory() throws Exception {
        assertEquals("0",Eval2.eval(new CodeGen(SOURCE).driver(CodeGen.Phase.TypeCheck),0));
        TestC.runSF(SOURCE,"global_assignment","",-1);
    }

    @Test public void rejectsFinalGlobal() {
        try {
            new CodeGen("val value=1; val set={ -> value=2; }; return set();")
                .driver(CodeGen.Phase.TypeCheck);
            fail("Assigned a final global");
        } catch( Parser.ParseException e ) {
            assertEquals("Cannot reassign final 'value'",e.getMessage());
        }
    }

    @Test public void rejectsIncompatibleGlobalValue() {
        try {
            new CodeGen("struct S { int x; }; int value=1; val set={ -> value=new S; }; set(); return value;")
                .driver(CodeGen.Phase.TypeCheck);
            fail("Stored an object in an integer global");
        } catch( Parser.ParseException expected ) {
            assertTrue(expected.getMessage(),expected.getMessage().contains("not of declared type"));
        }
    }
}
