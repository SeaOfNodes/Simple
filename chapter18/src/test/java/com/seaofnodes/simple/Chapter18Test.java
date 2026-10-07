package com.seaofnodes.simple;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.node.*;


import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assert.fail;
import org.junit.Ignore;

public class Chapter18Test {
    @Test public void testFloatArrays() {
        for( String type : new String[]{"flt","f64","f32"} ) {
            CodeGen zero = new CodeGen("var a = new "+type+"[arg]; return a[arg-1];")
                .parse().opto().typeCheck();
            assertEquals("0.0",Eval2.eval(zero,3));
            new CodeGen(type+" x = arg; var a = new "+type+"[arg]; a[0] = x; return a[0];")
                .parse().opto().typeCheck();
            CodeGen loop = new CodeGen("var a = new "+type+"[arg]; "+
                "for(int i=0; i<arg; i++) a[i] += i+0.25; return a[0]+a[arg-1];")
                .parse().opto().typeCheck();
            assertEquals("0.5",Eval2.eval(loop,1));
            assertEquals("3.5",Eval2.eval(loop,4));

            CodeGen rounded = new CodeGen("var a = new "+type+"[arg]; "+
                "for(int i=0; i<arg; i++) a[i] = 16777216.0+i; return a[arg-1];")
                .parse().opto().typeCheck();
            assertEquals(type.equals("f32") ? "1.6777216E7" : "1.6777217E7",Eval2.eval(rounded,2));
        }
        CodeGen rounded = new CodeGen("var a = new f32[arg]; "+
            "for(int i=0; i<arg; i++) a[i] = 16777217.0; return a[arg-1];")
            .parse().opto().typeCheck();
        assertEquals("1.6777216E7",Eval2.eval(rounded,2));
    }

    @Test public void testNeverExitLoopDepth() {
        String[] sources = {
            "if(arg) while(1) {} return 7;",
            "if(arg==1) while(1) {} if(arg==2) while(1) {} return 7;",
            "while(1) {}",
            "int x=0; while(arg) { if(arg==2) while(1) { x++; } x++; arg--; } return x;"
        };
        for( String src : sources ) {
            CodeGen code = new CodeGen(src).parse().opto().typeCheck();
            code._start.buildLoopTree(code._stop);
            int[] nevers = {0};
            code._stop.walk(n -> {
                if( n instanceof FunNode fun )
                    assertEquals("Function entry is outside its loops",0,fun.loopDepth());
                if( n instanceof ReturnNode ret ) {
                    assertEquals(ret.fun().loopDepth(),ret.loopDepth());
                    assertEquals(ret.fun().loopDepth(),ret.cfg0().loopDepth());
                }
                if( n instanceof LoopNode loop && !(loop instanceof StartNode) &&
                    loop.back() instanceof CProjNode back && back.in(0) instanceof NeverNode never ) {
                    nevers[0]++;
                    assertSame(loop,never.loop());
                    assertSame(loop,back.loop());
                    CProjNode exit = never.cproj(1-back._idx);
                    CFGNode fun = loop;
                    while( !(fun instanceof FunNode) ) fun=fun.idom();
                    assertSame(fun.loop(),exit.loop());
                    assertEquals(fun.loopDepth(),exit.loopDepth());
                    assertTrue(loop.loopDepth()>fun.loopDepth());
                }
                return null;
            });
            assertTrue("Exercise a synthetic exit",nevers[0]>0);
        }
    }

    @Test public void testFunctionLocalConstantChains() {
        String src = "val f = { int x -> x ? f(x-1)*305420988+x/305420988 : 1; }; "+
                     "val g = { int x -> x ? g(x-1)/305420988+x*305420988 : 1; }; "+
                     "return f(arg)+g(arg);";
        CodeGen code = new CodeGen(src);
        code.parse().opto().typeCheck();
        Node con = code._stop.walk(n -> n instanceof ConstantNode &&
                                  n._type==TypeInteger.constant(305420988) ? n : null);
        assertNotNull(con);
        Node[] uses = con._outputs.asAry();
        // Preserve a stacked Cast/Constant chain through scheduling.
        Node cast = new CastNode(TypeInteger.BOT,code._start,con);
        cast = new CastNode(TypeInteger.BOT,code._start,cast);
        for( Node use : uses )
            for( int i=1; i<use.nIns(); i++ )
                if( use.in(i)==con ) use.setDef(i,cast);
        code.GCM();
        var owners = new java.util.IdentityHashMap<CFGNode,Boolean>();
        code._stop.walk(n -> {
            if( !(n instanceof CastNode) || !(n.in(1) instanceof CastNode) ) return null;
            CFGNode fun = n.cfg0();
            while( fun!=null && !(fun instanceof FunNode) ) fun = fun.idom();
            assertTrue("Constant must belong to a function",fun instanceof FunNode);
            assertNull("Share one constant chain within each function",owners.put(fun,true));
            // Follow all parts of the constant chain, including shared inputs.
            var todo = new java.util.ArrayList<Node>();
            var seen = new java.util.IdentityHashMap<Node,Boolean>();
            todo.add(n);
            for( int j=0; j<todo.size(); j++ ) {
                Node part = todo.get(j);
                if( seen.put(part,true)!=null ) continue;
                CFGNode owner = part.cfg0();
                while( owner!=null && !(owner instanceof FunNode) ) owner = owner.idom();
                assertSame("Every constant-building operation is function-local",fun,owner);
                for( int i=1; i<part.nIns(); i++ ) {
                    Node def = part.in(i);
                    if( def==null || def instanceof CFGNode ) continue;
                    assertTrue("Copies must register their data edges",def._outputs.find(part)!=-1);
                    if( def._type.isConstant() ) todo.add(def);
                }
            }
            return null;
        });
        assertTrue("Exercise constants shared across functions",owners.size()>=2);
    }

    @Test public void testReachableMixedReturns() {
        for( String[] test : new String[][] {
            {"if(arg) return 7; else return 2.5;", "No common type amongst int and f64"},
            {"struct S { int x; }; if(arg) return new S; else return 0;",
             "No common type amongst int and reference"}
        } ) {
            try {
                new CodeGen(test[0]).parse().opto().typeCheck();
                fail("Expected incompatible return types: " + test[0]);
            } catch( Parser.ParseException e ) {
                assertEquals(test[1],e.getMessage());
            }
        }
    }


    @Test public void testPrintingForwardReferenceScope() throws Exception {
        new CodeGen("return 0;").parse();
        var declared = TypeMemPtr.make(TypeStruct.makeFRef("PrintForward"));
        var scope = new ScopeNode();
        scope.define("ptr",declared,false,new ConstantNode(TypeInteger.ZERO),null);
        Parser.TYPES.put("PrintForward",TypeMemPtr.make(TypeStruct.make("PrintForward")));
        var field = Var.class.getDeclaredField("_type");
        field.setAccessible(true);
        var v = scope._vars.get(0);
        org.junit.Assert.assertSame(declared,field.get(v));
        scope.toString();
        org.junit.Assert.assertSame(declared,field.get(v));
    }


    @Test
    public void testJig() {
        CodeGen code = new CodeGen(
"""
return 0;
""");
        code.parse().opto().typeCheck().GCM().localSched();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  2));
    }

    @Test
    public void testPhiParalleAssign() {
        CodeGen code = new CodeGen(
"""
int a = 1;
int b = 2;
while(arg--) {
  int t = a;
  a = b;
  b = t;
}
return a;
""");
        code.parse().opto().typeCheck().GCM();
        assertEquals("return Phi(Loop,1,Phi(Loop,2,Phi_a));", code._stop.toString());
        assertEquals("1", Eval2.eval(code,  0));
        assertEquals("2", Eval2.eval(code,  1));
        assertEquals("1", Eval2.eval(code,  2));
        assertEquals("2", Eval2.eval(code,  3));
    }


    // ---------------------------------------------------------------
    @Test
    public void testType0() {
        CodeGen code = new CodeGen(
"""
{int -> int}? x2 = null; // null function ptr
return x2;
""");
        code.parse().opto();
        assertEquals("return null;", code._stop.toString());
        assertEquals("null", Eval2.eval(code, 0 ) );
    }

    @Test
    public void testFcn0() {
        CodeGen code = new CodeGen(
"""
{int -> int}? sq = { int x ->
    x*x;
};
return sq;
""");
        code.parse().opto();
        assertEquals("Stop[ return { sq}; return (Parm_x(sq,int)*x); ]", code._stop.toString());
        assertEquals("{ sq}", Eval2.eval(code, 3));
    }

    @Test
    public void testFcn1() {
        CodeGen code = new CodeGen(
"""
var sq = { int x ->
    x*x;
};
return sq(arg)+sq(3);
""");
        code.parse().opto();
        assertEquals("Stop[ return (sq( 3)+sq( arg)); return (Parm_x(sq,int,3,arg)*x); ]", code._stop.toString());
        assertEquals("13", Eval2.eval(code, 2));
    }

    // Function scope test
    @Test
    public void testFcn2() {
        CodeGen code = new CodeGen(
"""
int cnt=1;
return { -> cnt; };
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("Variable 'cnt' is out of function scope and must be a final constant",e.getMessage()); }
    }

    // Function scope test
    @Test
    public void testFcn3() {
        CodeGen code = new CodeGen(
"""
val cnt=2;
return { -> cnt; }();
""");
        code.parse().opto();
        assertEquals("return 2;", code._stop.toString());
        assertEquals("2", Eval2.eval(code, 0));
    }

    // Function variables
    @Test
    public void testFcn4() {
        CodeGen code = new CodeGen(
"""
var fcn = arg ? { int x -> x*x; } : { int x -> x+x; };
return fcn(3);
""");
        code.parse().opto();
        assertEquals("Stop[ return Phi(Region,{ int -> int #1},{ int -> int #2})( 3); return (Parm_x($fun,int,3)*x); return (Parm_x($fun,int,3)*2); ]", code._stop.toString());
        assertEquals("6", Eval2.eval(code, 0));
        assertEquals("9", Eval2.eval(code, 1));
    }

    // Recursive factorial test
    @Test
    public void testFcn5() {
        CodeGen code = new CodeGen("val fact = { int x -> x <= 1 ? 1 : x*fact(x-1); }; return fact(arg);");
        code.parse().opto();
        assertEquals("Stop[ return fact( arg); return Phi(Region,1,(Parm_x(fact,int,arg,(x-1))*fact( Sub))); ]", code._stop.toString());
        assertEquals( "1", Eval2.eval(code, 0));
        assertEquals( "1", Eval2.eval(code, 1));
        assertEquals( "2", Eval2.eval(code, 2));
        assertEquals( "6", Eval2.eval(code, 3));
        assertEquals("24", Eval2.eval(code, 4));
    }

    @Test
    public void testFcn6() {
        CodeGen code = new CodeGen(
"""
struct S { int i; };
val newS = { int x -> return new S { i=x; }; };
return newS(1).i;
""");
        code.parse().opto().typeCheck().GCM();
        assertEquals("return 1;", code._stop.toString());
        assertEquals("1", Eval2.eval(code,  0));
    }

    // Double forward reference
    @Test
    public void testFcn7() {
        CodeGen code = new CodeGen(
"""
if( arg ? f : g ) return 1;
val f = {->1;};
val g = {->2;};
return 2;
""");
        code.parse().opto().typeCheck().GCM();
        assertEquals("Stop[ return 1; return 1; return 2; ]", code._stop.toString());
        assertEquals("1", Eval2.eval(code,  0));
    }

    @Test
    public void testFcn8() {
        CodeGen code = new CodeGen(
"""
{int -> int}? !i2i = null;
var id = {{int->int} f-> return f;};
for(;;) {
    if (i2i) return i2i(arg);
    var x = {int i-> return i;};
    arg = x(3);
    i2i = id(x);
}
""");
        code.parse().opto().typeCheck().GCM().localSched();
        assertEquals("Stop[ return x( Phi(Loop,arg,x( 3))); return Parm_i(x,int,3,Phi_arg); ]", code._stop.toString());
        assertEquals("3", Eval2.eval(code,  0));
    }


    @Test
    public void testFcn9() {
        CodeGen code = new CodeGen(
"""
{int -> int}? !i2i = null;
for(;;) {
    if (i2i) return i2i(arg);
    var x = {int i-> return i;};
    arg = x(3);
}
""");
        code.parse().opto().typeCheck().GCM().localSched();
        assertEquals("return Top;", code._stop.toString());
        assertEquals(null, Eval2.eval(code,  0));
    }

    // Function break
    @Test
    public void testErr1() {
        CodeGen code = new CodeGen(
"""
for(;;) {
    val f = { ->
        break;
    };
    f();
    return 2;
}
return 1;
""");
        try { code.parse().opto(); fail(); }
        catch( Exception e ) { assertEquals("No active loop for a break or continue",e.getMessage()); }
    }

    // Calling and inlining a null function
    @Test
    public void testErr2() {
        CodeGen code = new CodeGen(
"""
{int -> int}? !i2i = { int i -> return i; };
for(;;) {
    if (i2i(2) == arg) break;
    i2i = null;
}
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Might be null calling { int -> int #1}?",e.getMessage()); }
    }


    @Test
    public void testErr3() {
        CodeGen code = new CodeGen(
"""
val f = { int i, int j -> return i+j; };
return f();
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Expecting 2 arguments, but found 0",e.getMessage()); }
    }


    @Test
    public void testErr4() {
        CodeGen code = new CodeGen(
"""
struct S {
    {int} f = { -> x(); return 0; }; // Do not let fref x be a field
};
val x = { -> return 1; };
!S? !s = null;
for(;;) {
    if (s) return s.x;
}
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("Accessing unknown field 'x' from '*S'",e.getMessage()); }
    }

    @Test
    public void testErr5() {
        CodeGen code = new CodeGen(
"""
val g = { ->
    g();
};
return 0;
""");
        try { code.parse().opto().typeCheck(); fail(); }
        catch( Exception e ) { assertEquals("No defined return type",e.getMessage()); }
    }


    // Mutual recursion.  Fails without SCCP to lift the recursive return types.
    @Ignore @Test
    public void testFcnMutRec() {
        CodeGen code = new CodeGen(
"""
val is_even = { int x -> x ? is_odd (x-1) : true ; };
val is_odd  = { int x -> x ? is_even(x-1) : false; };
return is_even(arg);
""");
        code.parse().opto();
        assertEquals("Stop[ return is_even( arg); return Phi(Region,Phi(Region,is_even( ((Parm_x(is_even,int,arg,Sub)-1)-1)),0),1); ]", code._stop.toString());
        assertEquals("1", Eval2.eval(code, 0));
        assertEquals("0", Eval2.eval(code, 1));
        assertEquals("1", Eval2.eval(code, 2));
        assertEquals("0", Eval2.eval(code, 3));
    }

    // Forward ref to 'x' means that
    @Ignore @Test
    public void testForwardRef1() {
        CodeGen code = new CodeGen(
"""
struct S {
    { int } f = { -> return x(); };
};
val x = { -> return 1; };
!S? !s = null;
for(;;) {
    if (s) return s.f;
}
""");
        code.parse().opto().typeCheck().GCM();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  0));
    }

    // Inline hidden called more than once
    @Test
    public void testInline() {
        CodeGen code = new CodeGen(
"""
{int->int}?! i2i = {int i->return i;};
{{int->int}->{int->int}}! f2f = {{int->int} f->return f;};
val o = i2i;
if (arg) i2i = null;
if (i2i) return i2i(arg);
return f2f(o)(1);
""");
        code.parse().opto().typeCheck().GCM().localSched();
        assertEquals("Stop[ return Phi(Region,o( arg),o( 1)); return Parm_i(o,int,arg,1); ]", code._stop.toString());
        assertEquals("1", Eval2.eval(code,  2));
    }

    @Test
    public void testOperField() {
        CodeGen code = new CodeGen(
"""
struct Person {
    int coffee_count;
};
!Person !p = new Person;
p.coffee_count += 1;
return p.coffee_count;
""");
        code.parse().opto().typeCheck().GCM().localSched();
        assertEquals("return 1;", code._stop.toString());
        assertEquals("1", Eval2.eval(code,  2));
    }

    static final String CALL_MEMORY = """
        struct S { int x; int y; };
        val bump = { !S !s, int d -> int old=s.x; s.x=old+d; return old; };
        !S !a = new S { x=10; y=7; };
        !S !b = new S { x=20; y=9; };
        !S !p=a; if (arg) p=b;
        int before=p.x;
        int old=bump(p,3);
        int after=p.x;
        bump(a,5);
        return before*1000000+old*10000+after*100+a.x+a.y;
        """;

    @Test public void testCallMemory() {
        var code = new CodeGen(CALL_MEMORY).parse().opto();
        int[] calls={0};
        code._stop.walk(n -> { if(n instanceof CallNode) calls[0]++; return null; });
        assertEquals(2,calls[0]); // Keep real calls so this checks scheduling across them.
        assertEquals("10101325",Eval2.eval(code,0));
        assertEquals("20202322",Eval2.eval(code,1));
        code._stop.walk(n -> {
            if(n instanceof CallNode call) {
                assertEquals(5,call.nIns()); // No scheduling edges may become extra arguments.
                assertTrue(call.fptr()._type instanceof TypeFunPtr);
            }
            return null;
        });
    }

    static final String RECURSIVE_MEMORY = """
        struct S { int x; };
        val rec = { !S !s, int n ->
            if (n==0) return s.x;
            int before=s.x;
            s.x=before+1;
            return before+rec(s,n-1);
        };
        !S !s=new S { x=10; };
        int value=rec(s,arg);
        return value*100+s.x;
        """;

    @Test public void testRecursiveMemory() {
        var code = new CodeGen(RECURSIVE_MEMORY).parse().opto();
        for(int n=0; n<7; n++)
            assertEquals(Long.toString((10L*(n+1)+n*(n+1)/2)*100+10+n),Eval2.eval(code,n));
    }

    static final String INLINE_MEMORY = """
        struct S { int x; int y; };
        val make = { !S s ->
            S t = new S { x=s.x+1; y=3; };
            s.y=9;
            return t;
        };
        !S !a = new S { x=arg+40; y=7; };
        S t=make(a);
        return a.x*10000+a.y*100+t.x+t.y;
        """;

    @Test public void testInlineAllocationMemory() {
        var code = new CodeGen(INLINE_MEMORY).parse().opto();
        assertNull(code._stop.walk(n -> n instanceof CallNode ? n : null));
        assertEquals("400944",Eval2.eval(code,0));
        assertEquals("420946",Eval2.eval(code,2));
    }
}
