package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.parser.Parser;
import org.junit.Test;
import static org.junit.Assert.*;

@org.junit.Ignore("Deferred: inferred field requirements and incomplete-object traversal")
public class ConstructionRequirementsTest {
    private static final String CHILD = """
        struct Child {
            Test.Parent parent;
            int~ width;
            new Child = { Test.Parent ^par ->
                parent = par;
                width = par.width;
            };
        };
        """;

    private String parent(String body) {
        return CHILD+"struct Parent { Child child; int~ width; new Parent = { int n -> "+body.replace("arg","n")+" }; };";
    }

    private void rejects(String src, String message) {
        try {
            new CodeGen(src).parse();
            fail("Accepted: "+src);
        } catch( Parser.ParseException e ) {
            assertTrue(e.getMessage(),e.getMessage().contains(message));
        }
    }

    @Test public void testCycleAndRequiredField() {
        String src = parent("width = arg+10; child = new Child(self);")+"return new Parent(arg).child.parent.child.width;";
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.TypeCheck);
        assertEquals("17",Eval2.eval(code,7));
        assertEquals("10",Eval2.eval(code,0));
        code = new CodeGen(parent("width = arg+10; child = new Child(self);")+
            "val p = new Parent(arg); return new Child(p).parent.child.width;").driver(CodeGen.Phase.TypeCheck);
        assertEquals("17",Eval2.eval(code,7));
        code = new CodeGen(CHILD+"struct Parent { int width = 10; Child child = new Child(self); }; return new Parent().child.width;")
            .driver(CodeGen.Phase.TypeCheck);
        assertEquals("10",Eval2.eval(code,0));
        rejects(parent("child = new Child(self); width = 10;")+"return 0;","requires initialized field 'width'");
        rejects(parent("if(arg) width = 10; child = new Child(self); width = 20;")+"return 0;","requires initialized field 'width'");
        code = new CodeGen(parent("child = new Child(self); width = 10;").replace("width = par.width;","width = 5;")+
            "return new Parent(arg).child.parent.width;").driver(CodeGen.Phase.TypeCheck);
        assertEquals("10",Eval2.eval(code,0));
        // Even if the comparison folds using the non-null declared type,
        // accessing child still requires that field to have been initialized.
        rejects(parent("width = 10; child = new Child(self);").replace("width = par.width;","width = par.child==null;")+
            "return 0;","requires initialized field 'child'");
    }

    @Test public void testNoEscapeOrFieldLaundering() {
        rejects("struct C { C next = self; int~ payload; new C = { -> payload = next.payload; }; }; return 0;",
                "might not be initialized");
        CodeGen code = new CodeGen("struct C { C next = self; int payload; new C = { -> payload = 17; payload = next.payload; }; }; return new C().next.payload;")
            .driver(CodeGen.Phase.TypeCheck);
        assertEquals("17",Eval2.eval(code,0));
        rejects("""
            struct C {
                C next; int~ payload;
                new C = { -> next = self; payload = next.payload; };
            }; return 0;
            ""","might not be initialized");
        rejects(parent("width = 10; child = new Child(self); use(child);")+
                "val use = { Child c -> return c.parent.width; }; return 0;","ordinary function");
        rejects(CHILD+"""
            struct Parent {
                Child child; int~ width;
                new Parent = { -> width = 10; child = new Child(self); int x = child.parent.width; };
            }; return 0;
            ""","Traversing an unfinished reference field");
        rejects("""
            struct C {
                int x;
                val get = { -> return x; };
                new C = { -> x = get(); };
            }; return 0;
            ""","ordinary function");
        rejects("""
            struct Box { Test.C? value; }; val box = new Box;
            struct C { new C = { -> box.value = self; }; }; return 0;
            ""","ordinary object");
    }

    @Test public void testConstructorOnlyQualifier() {
        rejects("struct C {}; val f = { C ^c -> return 0; }; return 0;","constructor");
        rejects("struct C { new C = { int ^x -> }; }; return 0;","struct parameter");
        String src = parent("width = 10; child = new Child(self);")+"return 0;";
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.TypeCheck);
        try {
            code.serialize();
            fail("Exported an unrecorded construction contract");
        } catch( Parser.ParseException e ) {
            assertTrue(e.getMessage(),e.getMessage().contains("Separate compilation"));
        }
    }

    @Test public void testTransitiveRequirementsAndAliases() {
        String child = """
            struct Leaf {
                int~ width;
                new Leaf = { Test.Parent ^par -> val alias = par; width = alias.width; };
            };
            struct Child {
                Test.Parent parent; Leaf leaf;
                new Child = { Test.Parent ^par -> parent = par; leaf = new Leaf(par); };
            };
            """;
        String parent = "struct Parent { Child child; int~ width; new Parent = { int n -> %s }; };";
        String good = child+parent.formatted("width = n+10; val alias = self; child = new Child(alias);");
        CodeGen code = new CodeGen(good+"return new Parent(arg).child.leaf.width;").driver(CodeGen.Phase.TypeCheck);
        assertEquals("17",Eval2.eval(code,7));
        rejects(child+parent.formatted("child = new Child(self); width = 10;")+"return 0;",
                "requires initialized field 'width'");
    }

    @Test public void testLoopsAndUninlinedConstructor() {
        String src = parent("width = 1; for(int i = 0; i < arg; i++) width += 2; child = new Child(self);")+
            "return new Parent(arg).child.width;";
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code = new CodeGen(src.replace("Child","Child_noInline"),seed,true).driver(CodeGen.Phase.TypeCheck);
            assertEquals("7",Eval2.eval(code,3));
            assertEquals("1",Eval2.eval(code,0));
        }
        new CodeGen(src.replace("Child","Child_noInline"))
            .driver(CodeGen.Phase.Encoding,TestC.CPU_PORT,TestC.CALL_CONVENTION,true,false,0);
        rejects(parent("for(int i = 0; i < arg; i++) width = 2; child = new Child(self); width = 10;")+"return 0;",
                "requires initialized field 'width'");
        // The assignment on the backedge introduces the unfinished alias
        // after the call was parsed. It must not pass as an ordinary Child.
        rejects(parent("width = 10; child = new Child(self); Child? !c = null; while(arg--) { use(c); c = child; }")+
            "val use = { Child? c -> return 0; }; return 0;","ordinary function");
    }

    @Test public void testNullableFieldAndDuplicateArguments() {
        String src = """
            struct Child {
                Test.Parent parent; int~ width;
                new Child = { Test.Parent ^a, Test.Parent ^b ->
                    parent = a; width = a.width+b.width+(a.optional==null);
                };
            };
            struct Parent {
                Child child; int width; Child? optional;
                new Parent = { -> width = 10; child = new Child(self,self); };
            };
            return new Parent().child.width;
            """;
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.TypeCheck);
        assertEquals("21",Eval2.eval(code,0));
    }

    @Test public void testNullableUnfinishedParameter() {
        String child = """
            struct Child {
                Test.Parent? parent; int width;
                new Child = { Test.Parent? ^par ->
                    parent = par; width = par ? par.width : 0;
                };
            };
            val read = { Child c -> return c.width; };
            """;
        String parent = """
            struct Parent {
                Child child; int~ width;
                new Parent = { int n ->
                    val detached = new Child(null);
                    width = n+read(detached);
                    child = new Child(self);
                };
            };
            """;
        CodeGen code = new CodeGen(child+parent+"""
            val p = new Parent(arg);
            val leaf = new Child(p);
            return read(leaf)+read(p.child);
            """).driver(CodeGen.Phase.TypeCheck);
        assertEquals("14",Eval2.eval(code,7));
        assertEquals("0",Eval2.eval(code,0));
        rejects(child+parent.replace("width = n+read(detached);","").replace("child = new Child(self);",
                    "child = new Child(self); width = n;"),"requires initialized field 'width'");
        rejects(child+parent.replace("child = new Child(self);","child = new Child(self); read(child);"),
                "ordinary function");
        try {
            new CodeGen(child.replace("par ? par.width : 0","par.width")+parent+
                "return new Child(null).width;").driver(CodeGen.Phase.TypeCheck);
            fail("Dereferenced null constructor argument");
        } catch( Parser.ParseException e ) {
            assertTrue(e.getMessage(),e.getMessage().toLowerCase().contains("null"));
        }
    }

    @Test public void testDistinctCallTimeMemory() {
        String src = CHILD+"""
            struct Parent {
                Child child; Child second; int~ width;
                new Parent = { int n ->
                    width = n+1; child = new Child(self);
                    width = n+2; second = new Child(self);
                    width = n+3;
                };
            };
            val p = new Parent(arg); return p.child.width*100+p.second.width*10+p.width;
            """;
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code = new CodeGen(src.replace("Child","Child_noInline"),seed,true).driver(CodeGen.Phase.TypeCheck);
            assertEquals("123",Eval2.eval(code,0));
            assertEquals("456",Eval2.eval(code,3));
        }
        new CodeGen(src.replace("Child","Child_noInline"))
            .driver(CodeGen.Phase.Encoding,TestC.CPU_PORT,TestC.CALL_CONVENTION,true,false,0);
        String loop = parent("width = 1; child = new Child(self); for(int i = 0; i < arg; i++) { width += 1; child = new Child(self); } width += 10;")+
            "val p = new Parent(arg); return p.child.width*100+p.width;";
        CodeGen code = new CodeGen(loop.replace("Child","Child_noInline")).driver(CodeGen.Phase.TypeCheck);
        assertEquals("414",Eval2.eval(code,3));
        assertEquals("111",Eval2.eval(code,0));
    }
}
