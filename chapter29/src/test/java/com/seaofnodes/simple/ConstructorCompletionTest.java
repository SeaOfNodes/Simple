package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.parser.Parser;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConstructorCompletionTest {
    @Test public void testFieldMemoryDuringInitialization() {
        CodeGen code = new CodeGen("""
            struct A {
                int x = 1;
                x = 2;
                int y = self.x;
                new A = { -> self.x = 3; y += x; };
            };
            val a = new A; return a.x*10+a.y;
            """).driver(CodeGen.Phase.TypeCheck);
        assertEquals("35",Eval2.eval(code,0));
        code = new CodeGen("""
            struct A {
                int[] data = new int[3];
                new A = { -> for(int i = 0; i < 3; i++) data[i] = i+1; };
            };
            return new A().data[2];
            """).driver(CodeGen.Phase.TypeCheck);
        assertEquals("3",Eval2.eval(code,0));
        for( String body : new String[]{
            "int~ x; int y = x; new A = { -> x = 7; };",
            "int~ x; int y = x*0; new A = { -> x = 7; };",
            // Constructor entry includes the initializer's early return.
            // Its later Store of 2 must not excuse the earlier read of x.
            "int~ x; if(arg) return 0; x = 1; new A = { -> int y = x; x = 2; };"
        } )
            try {
                new CodeGen("struct A { "+body+" }; return 0;").parse();
                fail("Read a field before its constructor wrote it");
            } catch( Parser.ParseException e ) {
                assertTrue(e.getMessage(),e.getMessage().contains("might not be initialized"));
            }
    }

    @Test public void testRequiredNumberBesideNullableDefault() {
        CodeGen code = new CodeGen("""
            struct A { int~ x; A? p; new A = { -> x = 10; }; };
            return new A().x;
            """).driver(CodeGen.Phase.TypeCheck);
        assertEquals("10",Eval2.eval(code,0));
    }

    private void rejects(String fields, String field, boolean explicit) {
        String src = "struct B {}; struct A { "+fields+" }; return 0;";
        try {
            new CodeGen(src).parse();
            fail(src);
        } catch( Parser.ParseException e ) {
            assertEquals("'Test.A' is not fully initialized, field '"+field+
                         (explicit ? "' is only partially set in the constructor" :
                                     "' needs to be set in a constructor"),e.getMessage());
        }
    }

    @Test public void testMissingNumericFields() {
        for( String type : new String[]{"int", "u8", "f32"} ) {
            rejects(type+"~ x;","x",false);
            rejects(type+"~ x; new A = { -> };","x",true);
            // Converting the merged BOTTOM to the numeric storage type used
            // to hide the missing assignment from the returned memory.
            rejects(type+"~ x; new A = { -> if(arg) x = 1; };","x",true);
            rejects(type+"~ x; new A = { -> if(arg) return 0; x = 1; };","x",true);
            rejects(type+"~ x; new A = { -> while(arg--) x = 1; };","x",true);
        }
    }

    @Test public void testInitializerAndConstructorReturns() {
        rejects("B b;","b",false);
        rejects("new A = { -> if(arg) b = new B; }; B b;","b",true);
        String[] fields = {
            "int~ x; new A = { -> if(arg) { x = 3; return 0; } x = 7; };",
            // <init> has no value for x; the user constructor supplies it.
            "new A = { -> x = 7; }; int~ x;",
            // The user constructor inherits the declaration's default.
            "new A = { -> }; int~ x = 7;",
            "B? b; int x;",
        };
        String[][] values = {{"7","3"}, {"7","7"}, {"7","7"}, {"0","0"}};
        for( int i = 0; i<fields.length; i++ ) {
            CodeGen code = new CodeGen("struct B {}; struct A { "+fields[i]+" }; return new A().x;")
                .driver(CodeGen.Phase.TypeCheck);
            assertEquals(values[i][0],Eval2.eval(code,0));
            assertEquals(values[i][1],Eval2.eval(code,1));
        }
    }

    @Test public void testNonreturningPaths() {
        // An initializer parsed in dead enclosing code can have TOP memory
        // rather than a struct-shaped result.
        new CodeGen("while(false) { struct A { int x; }; } return 0;").parse();
        new CodeGen("struct A { int~ x; new A = { -> while(true) {} }; }; return 0;").parse();
        rejects("int~ x; new A = { -> if(arg) return 0; while(true) {} };","x",true);
        CodeGen code = new CodeGen("""
            struct A {
                int~ x;
                new A = { -> if(arg) { x = 7; return 0; } while(true) {} };
            };
            return new A().x;
            """).driver(CodeGen.Phase.TypeCheck);
        assertEquals("7",Eval2.eval(code,1));
    }

    @Test public void testEarlierConstructorBinding() {
        // The older binding spelling is parsed before the following field.
        // Its Return lacks that field, so completion must consult <init> too.
        rejects("val A = { -> }; B b;","b",true);
        rejects("int~ x; val A = { -> if(arg) x = 1; };","x",true);
        new CodeGen("struct B {}; struct A { val A = { -> }; B b = new B; }; return 0;").parse();
        new CodeGen("struct A { val A = { -> while(true) {} }; int~ x; }; return 0;").parse();
    }
}
