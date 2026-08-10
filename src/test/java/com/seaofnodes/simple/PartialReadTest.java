package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.InitViewNode;
import com.seaofnodes.simple.node.ConstructNode;
import com.seaofnodes.simple.parser.Parser;
import org.junit.Test;
import static org.junit.Assert.*;

public class PartialReadTest {
    private static String program(String body, String helper) {
        return "struct _C { int~ x; int~ y; new _C = { int n -> " + body +
            " }; }; val _read_noInline = { _C ^p -> " + helper +
            " }; return new _C(((arg<<32)>>32)).y;";
    }
    private static void rejects(String source, String message) {
        try {
            new CodeGen(source).driver(CodeGen.Phase.TypeCheck);
            fail("Accepted " + source);
        } catch( Parser.ParseException e ) {
            assertTrue(e.getMessage(),e.getMessage().contains(message));
        }
    }
    @Test public void testLocalRead() {
        String src = program("x=n; y=_read_noInline(self);", "return p.x;");
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code = new CodeGen(src,seed,true).driver(CodeGen.Phase.TypeCheck);
            assertNull(code._stop.walk(n -> n instanceof InitViewNode ? n : null));
            assertEquals("42",Eval2.eval(code,42));
        }
        new CodeGen(src).driver(CodeGen.Phase.Encoding,TestC.CPU_PORT,TestC.CALL_CONVENTION,true,false,0);
    }
    @Test public void testRedundantViewsFoldBeforeTypeCheck() {
        String src=program("x=n; y=0; y=_read_noInline(self);", "return p.x;");
        CodeGen code=new CodeGen(src).driver(CodeGen.Phase.IterPostOpto);
        assertNull(code._stop.walk(n -> n instanceof InitViewNode ? n : null));
        assertNull(code._stop.walk(n -> n instanceof ConstructNode c && !c._partial ? n : null));
        code.driver(CodeGen.Phase.TypeCheck);
        assertEquals("31",Eval2.eval(code,31));

        // Here the view still masks y. A valid program need not initialize
        // every field before lending self, so survival is not itself an error.
        code=new CodeGen(program("x=n; y=_read_noInline(self);", "return p.x;"))
            .driver(CodeGen.Phase.IterPostOpto);
        assertNotNull(code._stop.walk(n -> n instanceof InitViewNode ? n : null));
        code.driver(CodeGen.Phase.TypeCheck);
        assertEquals("31",Eval2.eval(code,31));
    }
    @Test public void testInliningKeepsBorrowRestriction() {
        rejects("""
            struct _C {
                int !x;
                new _C={ int n -> x=n; if(!n) _write(self); };
            };
            val _write={ !_C ^p -> if(p.x) p.x=9; return p.x; };
            val a=new _C(0); val b=new _C(1);
            return _write(b)+a.x;
            ""","incomplete object");
    }
    @Test public void testMissingAndBranchField() {
        for( String use : new String[]{"p.x", "p.x*0", "p.x==p.x"} )
            rejects(program("y=_read_noInline(self); x=n;", "return "+use+";"),"might not be initialized");
        rejects(program("if(n) x=n; y=_read_noInline(self); x=0;", "return p.x;"),"might not be initialized");
    }
    @Test public void testActualValueFromMemory() {
        for( String body : new String[]{"x=1; val p=self; x=2; y=_read_noInline(p);",
                "val p=self; x=2; y=_read_noInline(p);"} ) {
            CodeGen code = new CodeGen(program(body,"return p.x;")).driver(CodeGen.Phase.TypeCheck);
            assertEquals("2",Eval2.eval(code,0));
        }
    }
    @Test public void testBorrowedWrite() {
        rejects(program("x=n; y=_read_noInline(self);", "p.x=9; return p.x;")
                .replace("int~ x", "int !x").replace("_C ^p", "!_C ^p"),"incomplete object");
    }
    @Test public void testUnknownCallerCannotRead() {
        rejects(program("x=n; y=_read_noInline(self);", "return p.x;")
                .replace("_read_noInline","read_noInline"),"might not be initialized");
    }
    @Test public void testForwardingAndMergedCallers() {
        String src = program("x=n; y=_read_noInline(self);", "return _next_noInline(p);")
            + " val _next_noInline = { _C ^p -> return p.x; };";
        assertEquals("17",Eval2.eval(new CodeGen(src).driver(CodeGen.Phase.TypeCheck),17));
        rejects(program("y=_read_noInline(self); x=n; y=_read_noInline(self);", "return p.x;"),"might not be initialized");
    }
    @Test public void testLoops() {
        String src = program("x=1; for(int i=0; i<n; i++) x+=2; y=_read_noInline(self);", "return p.x;");
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code = new CodeGen(src,seed,true).driver(CodeGen.Phase.TypeCheck);
            assertEquals("7",Eval2.eval(code,3));
            assertEquals("1",Eval2.eval(code,0));
        }
        rejects(program("for(int i=0; i<n; i++) x=n; y=_read_noInline(self); x=0;", "return p.x;"),"might not be initialized");
    }
    @Test public void testNoPublication() {
        rejects("struct Box { Test._C? !p; }; val box=new Box; " +
                program("x=n; y=_read_noInline(self);", "box.p=p; return p.x;"),"ordinary object");
    }
    @Test public void testConstructorReadAndRetention() {
        String src = """
            struct _Child {
                Test._Parent parent;
                int~ seen;
                new _Child = { Test._Parent ^p -> parent=p; seen=p.x; };
            };
            struct _Parent {
                int~ x;
                _Child child;
                new _Parent = { int n -> x=n; child=new _Child(self); };
            };
            val p=new _Parent(((arg<<32)>>32)); return p.child.seen+p.child.parent.x;
            """;
        assertEquals("22",Eval2.eval(new CodeGen(src).driver(CodeGen.Phase.TypeCheck),11));
        rejects(src.replace("x=n; child=new _Child(self);","child=new _Child(self); x=n;"),"might not be initialized");
    }
    @Test public void testInlineReadAndWrite() {
        String src=program("x=n; y=_read_noInline(self);","return p.x;").replace("_noInline","");
        assertEquals("19",Eval2.eval(new CodeGen(src).driver(CodeGen.Phase.TypeCheck),19));
        rejects(src.replace("return p.x;","p.x=9; return p.x;").replace("int~ x","int !x").replace("_C ^p","!_C ^p"),"incomplete object");
        rejects(src.replace("x=n; y=", "y=").replace("(self);", "(self); x=n;"),"might not be initialized");
    }
    @Test public void testArrayAndNestedReference() {
        String src="""
            struct _C {
                int[] data;
                int~ y;
                new _C = { int n -> data=new int[1]; data[0]=n; y=_read_noInline(self); };
            };
            val _read_noInline={ _C ^p -> return p.data[0]; };
            return new _C(((arg<<32)>>32)).y;
            """;
        assertEquals("23",Eval2.eval(new CodeGen(src).driver(CodeGen.Phase.TypeCheck),23));
        // A set reference slot is not proof that its referent is initialized.
        rejects("struct D { int x=7; }; "+src.replace("int[] data","D data")
                .replace("data=new int[1]; data[0]=n;","data=new D;")
                .replace("p.data[0]","p.data.x"),"might not be initialized");
    }
    @Test public void testCompleteBorrowCannotPublishPartial() {
        rejects("""
            struct _C { int x=7; };
            struct Box { _C? p; }; val box=new Box;
            val _store={ !_C ^dst, _C ^src -> box.p=src; return dst.x; };
            return _store(new _C,new _C);
            ""","ordinary object");
    }
    @Test public void testSameClassMemory() {
        String src=program("x=n; y=n ? _read_noInline(self) : x;",
                           "val other=new _C(0); return p.x+other.y;");
        for( long seed : new long[]{1,123,987654321} ) {
            CodeGen code=new CodeGen(src,seed,true).driver(CodeGen.Phase.TypeCheck);
            assertEquals("29",Eval2.eval(code,29));
            assertEquals("0",Eval2.eval(code,0));
        }
    }
}
