package com.seaofnodes.simple;

import org.junit.Test;
import static org.junit.Assert.*;
import com.seaofnodes.simple.codegen.CodeGen;

public class MutabilityTest {
    private static void ok(String src, String result) {
        var code = new CodeGen(src).parse().opto().typeCheck();
        assertEquals(result,Eval2.eval(code,0));
    }
    private static void bad(String src) {
        try {
            new CodeGen(src).parse().opto().typeCheck();
        } catch( RuntimeException expected ) {
            String msg = expected.getMessage();
            assertTrue("Expected a permission error, got: "+expected,
                msg!=null && (msg.contains("final") || msg.contains("not of declared type") || msg.contains("Cannot store") || msg.contains("Argument #")));
            return;
        }
        fail("Accepted forbidden write: "+src);
    }

    @Test public void testIndependentBindingsAndViews() {
        ok("struct P { int x; new P = { int v -> x=v; }; }; !P ~p=new P(0); p.x=7; return p.x;", "7");
        ok("struct P { int x; new P = { int v -> x=v; }; }; ~P !p=new P(3); p=new P(7); return p.x;", "7");
        bad("struct P { int x; new P = { int v -> x=v; }; }; !P ~p=new P(0); p=new P(0); return 0;");
        bad("struct P { int x; new P = { int v -> x=v; }; }; ~P !p=new P(0); p.x=7; return 0;");
        bad("int ~x=3; x=7; return x;");
        ok("int !x=3; x=7; return x;", "7");
    }

    @Test public void testDeepViewAndAliases() {
        ok("struct P { int x; new P = { int v -> x=v; }; }; !P ~p=new P(0); ~P ~r=p; p.x=7; return r.x;", "7");
        bad("struct P { int x; new P = { int v -> x=v; }; }; struct Box { !P p; new Box = { !P v -> p=v; }; }; !P p=new P(0); Box b=new Box(p); b.p.x=7; return 0;");
        bad("struct P { int x; new P = { int v -> x=v; }; }; P p=new P(0); !P q=p; q.x=7; return 0;");
        bad("struct P { P? next; int x; new P = { P? v -> next=v; }; }; !P p=new P(new P(null)); p.next.x=7; return 0;");
        bad("struct P { !P? next; int x; new P = { !P? v -> next=v; }; }; P p=new P(new P(null)); p.next.x=7; return 0;");
    }

    @Test public void testInferencePreservesAccess() {
        ok("struct P { int x; }; val p=new P; p.x=7; return p.x;", "7");
        bad("struct P { int x; }; val p=new P; p=new P; return 0;");
        bad("struct P { int x; }; P p=new P; var q=p; q.x=7; return 0;");
    }

    @Test public void testParameterPermissions() {
        ok("struct P { int x; }; val f={ !P p -> p.x=7; return p.x; }; return f(new P);", "7");
        ok("val f={ int ~x -> return x; }; return f(7);", "7");
        bad("val f={ int ~x -> x=7; return x; }; return f(3);");
        bad("struct P { int x; }; val f={ P p -> p.x=7; return p.x; }; return f(new P);");
        bad("struct P { int x; }; val f={ !P p -> p.x=7; return p.x; }; P p=new P; return f(p);");
        bad("struct P { int x; }; val f={ P?[] a -> return a#; }; !P?[] a=new !P?[1]; return f(a);");
    }

    @Test public void testArrayLayers() {
        bad("struct P { int x; }; !P?[] rw=new !P?[1]; P?[] ro=rw; rw[0]=new P; ro[0].x=7; return 0;");
        ok("u8[] a=new u8[2]; a[0]=7; return a[0];", "7");
        ok("struct P { int x; new P = { int v -> x=v; }; }; P?[] a=new P?[1]; a[0]=new P(7); return a[0].x;", "7");
        bad("struct P { int x; new P = { int v -> x=v; }; }; P?[] a=new P?[1]; a[0]=new P(0); a[0].x=7; return 0;");
        ok("struct P { int x; new P = { int v -> x=v; }; }; !P?[] a=new !P?[1]; a[0]=new P(0); a[0].x=7; return a[0].x;", "7");
        bad("u8[~] a=new u8[1]; a[0]=7; return 0;");
        ok("u8[~]?[] rows=new u8[~]?[1]; rows[0]=new u8[1]; return rows#;", "1");
        bad("u8[~]?[] rows=new u8[~]?[1]; rows[0]=new u8[1]; rows[0][0]=7; return 0;");
        bad("u8[]?[~] rows=new u8[]?[1]; rows[0]=new u8[1]; return 0;");
    }
}
