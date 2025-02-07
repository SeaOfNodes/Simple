package com.seaofnodes.simple;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.node.*;


import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assert.fail;
import org.junit.Ignore;

public class Chapter18Test {
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

    @Test public void testPrintingLazyMemory() throws Exception {
        var code = new CodeGen("return 0;").parse();
        var base = new ConstantNode(com.seaofnodes.simple.type.TypeMem.BOT);
        var outer = new ScopeNode();
        var inner = new ScopeNode();
        var loop = new LoopNode(null,code._start);
        loop._type = com.seaofnodes.simple.type.Type.CONTROL;
        var outerMem = new MemMergeNode(true);
        outerMem.addDef(null); outerMem.addDef(base); outerMem.addDef(base);
        outer.addDef(loop); outer.addDef(outerMem);
        var innerMem = new MemMergeNode(true);
        innerMem.addDef(null); innerMem.addDef(outer); innerMem.addDef(outer);
        inner.addDef(loop); inner.addDef(innerMem);
        var mem = new MemMergeNode(true);
        mem.addDef(null); mem.addDef(base); mem.addDef(inner);
        PrintTestSupport.unchanged(mem, () -> org.junit.Assert.assertTrue(mem.toString().contains("Lazy_")));
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
        assertEquals("{ int -> int #1}", Eval2.eval(code, 3));
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
        code.parse().opto().typeCheck().GCM().localSched();
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
        assertEquals("Stop[ return Phi(Region,{ int -> int #1},{ int -> int #2})( 3); return (Parm_x($fun,int,3)*x); return (Parm_x($fun,int,3)<<1); ]", code._stop.toString());
        assertEquals("6", Eval2.eval(code, 0));
        assertEquals("9", Eval2.eval(code, 1));
    }

    // Recursive factorial test
    @Test
    public void testFcn5() {
        CodeGen code = new CodeGen("val fact = { int x -> x <= 1 ? 1 : x*fact(x-1); }; return fact(arg);");
        code.parse().opto().typeCheck();
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
{int -> int}? i2i = null;
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
{int -> int}? i2i = null;
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


    @Test
    public void testFcn10() {
        CodeGen code = new CodeGen(
"""
struct Person {
  int age;
};

val fcn = { Person?[] ps, int x ->
  val tmp = ps[x];
  if( ps[x] )
    ps[x].age++;
};

var ps = new Person?[2];
ps[0] = new Person;
ps[1] = new Person;
fcn(ps,1);
""");
        code.parse().opto().typeCheck().GCM().localSched();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  0));
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
S? s = null;
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
S? s = null;
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
Person !p = new Person;
p.coffee_count += 1;
return p.coffee_count;
""");
        code.parse().opto().typeCheck().GCM().localSched();
        assertEquals("return 1;", code._stop.toString());
        assertEquals("1", Eval2.eval(code,  2));
    }

}
