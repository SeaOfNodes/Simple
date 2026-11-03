package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.parser.Parser;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConstructionGroupTest {
    private static final String CHILD = """
        struct Child {
            Test.Parent? parent;
            int value = 5;
            new Child = { Test.Parent? ^par -> parent = par; };
        };
        val read = { Child c -> return c.value; };
        """;

    private String parent(String body) {
        return CHILD+"struct Parent { Child child; int~ payload; new Parent = { int n -> "+body+" }; };";
    }

    private void rejects(String src, String message) {
        try {
            new CodeGen(src).parse();
            fail("Accepted: "+src);
        } catch( Parser.ParseException e ) {
            assertTrue(e.getMessage(),e.getMessage().contains(message));
        }
    }

    @Test public void testCyclesAndIndependentAllocations() {
        String src = parent("val detached = new Child(null); payload = n+read(detached); child = new Child(self);")+
            "val p = new Parent(arg); val local = new Child(p); val par = local.parent; return (par ? par.payload : 0)+p.child.value;";
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code = new CodeGen(src,seed,true).driver(CodeGen.Phase.TypeCheck);
            assertEquals("17",Eval2.eval(code,7));
            assertEquals("10",Eval2.eval(code,0));
        }
        new CodeGen(src.replace("Child","Child_noInline"))
            .driver(CodeGen.Phase.Encoding,TestC.CPU_PORT,TestC.CALL_CONVENTION,true,false,0);
    }

    @Test public void testNoIncompleteLookupOrEscape() {
        // This lookup supplies a live result. Inlining must not turn self
        // complete inside the body before checking the use of child.
        rejects(parent("child = new Child(self); payload = child.parent==null;")+
            "return new Parent(arg).payload;","incomplete object");
        rejects(parent("payload = 1; child = new Child(self); int x = child.parent==null;")+"return 0;","incomplete object");
        rejects(parent("payload = 1; child = new Child(self); read(child);")+"return 0;","ordinary function");
        rejects(parent("payload = 1; child = new Child(self);").replace("parent = par;","parent = par; value = par.payload;")+
            "return 0;","incomplete object");
        rejects("""
            struct Box { Test.C? value; }; val box = new Box;
            struct C { int~ x; new C = { -> box.value = self; x = 1; }; };
            return 0;
            ""","ordinary object");
        rejects("struct C { C next = self; int~ x; new C = { -> int y = next.x; x = 7; }; }; return 0;",
            "incomplete object");
    }

    @Test public void testAliasesAndLoopBackedges() {
        String src = parent("val alias = self; alias.payload = n; child = new Child(alias);")+"return new Parent(arg).child.value;";
        assertEquals("5",Eval2.eval(new CodeGen(src).driver(CodeGen.Phase.TypeCheck),3));
        rejects(parent("payload = 1; child = new Child(self); Child? !c = null; while(n--) { if(c) read(c); c = child; }")+
            "return 0;","ordinary function");
        rejects(parent("payload = 1; child = new Child(self); Parent? !p = null; while(n--) { val c = new Child(p); read(c); p = self; }")+
            "return 0;","ordinary function");
        rejects(parent("payload = 1; child = new Child(self); val c = n ? new Child(null) : child; read(c);")+
            "return 0;","ordinary function");
    }

    @Test public void testConstructorOnlyQualifier() {
        rejects("struct C {}; val f = { C ^c -> return 0; }; return 0;","constructor");
        rejects("struct C { new C = { int ^x -> }; }; return 0;","struct parameter");
        rejects(parent("payload = 1; child = new Child(self);").replace("^par","par")+"return 0;","without '^'");
        CodeGen code = new CodeGen(parent("payload = 1; child = new Child(self);")+"return 0;")
            .driver(CodeGen.Phase.TypeCheck);
        try {
            code.serialize();
            fail("Exported a constructor qualifier without its contract");
        } catch( Parser.ParseException e ) {
            assertTrue(e.getMessage(),e.getMessage().contains("Separate compilation"));
        }
    }

    @Test public void testCompleteLoopAllocations() {
        // Optimistic types prove the cycle p -> new Link(p) -> p complete.
        String src = """
            struct Link {
                Link? next;
                int value = 5;
                new Link = { Link? ^n -> next = n; };
            };
            struct Root {
                Link head;
                int value;
                new Root = { int n ->
                    Link? !p = null;
                    while(n--) p = new Link(p);
                    if(!p) p = new Link(null);
                    value = p.value;
                    head = p;
                };
            };
            val root = new Root(arg);
            return root.value + root.head.value;
            """;
        CodeGen code = new CodeGen(src).driver(CodeGen.Phase.TypeCheck);
        assertEquals("10",Eval2.eval(code,0));
        assertEquals("10",Eval2.eval(code,3));
        // Dead calls discard their arguments before a construction result exists.
        assertEquals("0",Eval2.eval(new CodeGen(CHILD+"struct Parent {}; return 0; new Child(null);")
            .driver(CodeGen.Phase.TypeCheck),0));
    }

    @Test public void testDeclaredQualifierAndOwnReceiver() {
        // A complete caller does not grant permission to read a ^ formal.
        rejects("""
            struct Parent { int payload = 7; };
            struct Child {
                int value;
                new Child = { Parent ^par -> value = par.payload; };
            };
            return new Child(new Parent).value;
            ""","incomplete object");
        // The self exception belongs to the receiver, not every pointer of its class.
        rejects("""
            struct C {
                int !value;
                new C = { !C? ^other -> if(other) other.value = 1; value = 2; };
            };
            return new C(new C(null)).value;
            ""","incomplete object");
    }
}
