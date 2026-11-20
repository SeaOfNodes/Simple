package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Small allocation poster children. Keep calls visible: this tests summaries,
 * not whether an inliner happens to put the allocations in the right function. */
public class Chapter33Test {
    private static final String THING = """
        struct Thing {
            i64 x;
            new Thing = { i64 x -> self.x=x; };
        };
        val read_noInline = { Thing t -> t.x; };
        """;

    private static CodeGen compile(String source) {
        return new CodeGen(source).driver(CodeGen.Phase.TypeCheck);
    }

    private static List<Allocation.Site> allocations(Allocation.Summary sum, String type) {
        sum.requireSupported();
        var sites = new ArrayList<Allocation.Site>();
        for( var site : sum._sites )
            if( site._allocation!=null && site._allocation._ts._name.endsWith("."+type) )
                sites.add(site);
        return sites;
    }

    // 1. A call borrows a temporary. The caller can reclaim it on return.
    @Test public void testAllocationLocalFrame() {
        CodeGen code = compile(THING+"""
            val work_noInline = { i64 n ->
                val t=new Thing(n);
                return read_noInline(t);
            };
            return work_noInline(arg);
            """);
        var sites = allocations(code._allocation.summary("work_noInline"),"Thing");
        assertEquals(1,sites.size());
        assertEquals("frame",sites.get(0).destination());
        assertEquals("17",Eval2.eval(code,17));
    }

    // 2. A factory returns a graph through another factory. Both objects need
    // result destinations, but the final consuming caller owns them locally.
    @Test public void testAllocationFactoryGraph() {
        CodeGen code=compile(THING+"""
            struct Box {
                Thing child;
                new Box = { Thing child -> self.child=child; };
            };
            val make_noInline = { i64 n -> new Box(new Thing(n)); };
            val forward_noInline = { i64 n -> make_noInline(n); };
            val use_noInline = { Box b -> b.child.x; };
            val work_noInline = { i64 n -> use_noInline(forward_noInline(n)); };
            return work_noInline(arg);
            """);
        for( String name : new String[]{"make_noInline","forward_noInline"} ) {
            var sum = code._allocation.summary(name);
            assertEquals("result",allocations(sum,"Box"  ).get(0).destination());
            assertEquals("result",allocations(sum,"Thing").get(0).destination());
        }
        var work = code._allocation.summary("work_noInline");
        assertEquals("frame",allocations(work,"Box"  ).get(0).destination());
        assertEquals("frame",allocations(work,"Thing").get(0).destination());
        assertEquals("23",Eval2.eval(code,23));
    }

    private static final String HOLDER = """
        struct Holder { Thing !c = new Thing(0); };
        """;

    // 3. A fresh reference escapes through a parameter, not the return value.
    @Test public void testAllocationStoreThroughArgument() {
        CodeGen code=compile(THING+HOLDER+"""
            val fill_noInline = { !Holder h, i64 n -> h.c = new Thing(n); return n; };
            val work_noInline = { i64 n ->
                val h = new Holder;
                fill_noInline(h,n);
                return h.c.x;
            };
            return work_noInline(arg);
            """);
        var fill = allocations(code._allocation.summary("fill_noInline"),"Thing");
        assertEquals(1,fill.size());
        assertEquals("arg3",fill.get(0).destination()); // arg2 is the class receiver
        for( var site : allocations(code._allocation.summary("work_noInline"),"Thing") )
            assertEquals("frame",site.destination());
        assertEquals("31",Eval2.eval(code,31));
    }

    // 4. One shared factory, two call sites, two unrelated destinations.
    @Test public void testAllocationSeparateDestinations() {
        CodeGen code=compile(THING+HOLDER+"""
            val make_noInline = { i64 n -> new Thing(n); };
            val fill_noInline = { !Holder a, !Holder b, i64 n ->
                a.c = make_noInline(n);
                b.c = make_noInline(n+1);
                return n;
            };
            val a = new Holder, b = new Holder;
            fill_noInline(a,b,arg);
            return a.c.x*10+b.c.x;
            """);
        var sites=allocations(code._allocation.summary("fill_noInline"),"Thing");
        assertEquals(2,sites.size());
        assertNotEquals(sites.get(0)._name,sites.get(1)._name);
        var destinations=new ArrayList<String>();
        for( var site : sites ) destinations.add(site.destination());
        Collections.sort(destinations);
        assertEquals(List.of("arg3","arg4"),destinations);
        assertEquals("45",Eval2.eval(code,4));
        String dump=code._allocation.toString();
        assertTrue(dump.contains("[arg3]"));
        assertTrue(dump.contains(".c -->"));
        assertTrue(dump.indexOf("+-- make_noInline\n") < dump.indexOf("+-- fill_noInline\n"));
    }

    // A join requires BOTH possible owners; it must not pick whichever input
    // happens to be visited first. Alias actual arguments at a call as well.
    @Test public void testAllocationConditionalAndAliasedOwners() {
        CodeGen code=compile(THING+HOLDER+"""
            val fill_noInline = { !Holder a, !Holder b, i64 n ->
                val h=n ? a : b;
                h.c=new Thing(n);
                return n;
            };
            val a=new Holder, b=new Holder;
            fill_noInline(a,b,arg);
            fill_noInline(a,a,arg+1);
            return a.c.x;
            """);
        var sites=allocations(code._allocation.summary("fill_noInline"),"Thing");
        assertEquals(1,sites.size());
        assertEquals("arg3 & arg4",sites.get(0).destination());
        assertEquals("8",Eval2.eval(code,7));
    }

    @Test public void testAllocationBorrowAndObjectCycle() {
        CodeGen borrow=compile(THING+"""
            val choose_noInline={ Thing a, Thing b, i64 n -> n ? a : b; };
            return read_noInline(choose_noInline(new Thing(arg),new Thing(arg+1),arg));
            """);
        var choose=borrow._allocation.summary("choose_noInline").requireSupported();
        assertEquals(2,choose._returns.size());
        var destinations=new ArrayList<String>();
        for( var site : choose._returns ) {
            assertNull(site._allocation);
            destinations.add(site.destination());
        }
        Collections.sort(destinations);
        assertEquals(List.of("result & arg3","result & arg4"),destinations);
        assertEquals("1",Eval2.eval(borrow,0));

        CodeGen cycle=compile("""
            struct Link { Link? !next; i64 x; new Link={ i64 n -> x=n; }; };
            val cycle_noInline={ i64 n ->
                val a=new Link(n), b=new Link(n+1);
                a.next=b;
                b.next=a;
                return a;
            };
            return cycle_noInline(arg).x;
            """);
        var sites=allocations(cycle._allocation.summary("cycle_noInline"),"Link");
        assertEquals(2,sites.size());
        for( var site : sites ) assertEquals("result",site.destination());
        assertEquals("9",Eval2.eval(cycle,9));
    }

    // Scalar results must not hide allocations when arithmetic is skipped.
    @Test public void testAllocationScalarArrayUse() {
        CodeGen code=compile("""
            val work_noInline={ i64 n ->
                val a=new i64[2];
                a[0]=n;
                return a[n&1]+n*n;
            };
            return work_noInline(arg);
            """);
        var sum=code._allocation.summary("work_noInline").requireSupported();
        int arrays=0;
        for( var site : sum._sites )
            if( site._allocation!=null && site._allocation._ts.isAry() ) {
                arrays++;
                assertEquals("frame",site.destination());
            }
        assertEquals(1,arrays);
        assertEquals("25",Eval2.eval(code,5));
        assertEquals("20",Eval2.eval(code,4));
    }

    // Make unsupported plans explicit, without replacing existing TypeCheck
    // diagnostics or changing the allocator used by older chapter tests.
    @Test public void testAllocationRejectedPlans() {
        String[] sources={
            "val bad_noInline={ i64 n -> n ? bad_noInline(n-1) : 0; }; return bad_noInline(arg);",
            "val bad_noInline={ i64 n -> i64 s=0; while(n>0) { s+=n; n--; } return s; }; return bad_noInline(arg);",
            "val bad_noInline={ i64 n -> new int[n]; }; return bad_noInline(arg);",
            THING+HOLDER+"val bad_noInline={ Holder h -> h.c; }; return bad_noInline(new Holder);",
            "{ i64 -> i64 } native=\"C\"; val bad_noInline={ i64 n -> native(n); }; return bad_noInline(arg);",
            "val bad_noInline={ -> val a=new int[2]; i64 raw=a; return raw+17; }; return bad_noInline();",
            "val bad_noInline={ int[] a -> i64 raw=a; return raw+17; }; return bad_noInline(new int[2]);"
        };
        String[] reasons={"recursive", "loop destinations", "variable-size", "LoadNode", "opaque", "raw address", "raw address"};
        for( int i=0; i<sources.length; i++ ) {
            CodeGen code=compile(sources[i]);
            var sum=code._allocation.summary("bad_noInline");
            assertFalse(sum.supported());
            assertTrue(sum._reject,sum._reject.contains(reasons[i]));
            try { sum.requireSupported(); fail("Rejected plan was usable"); }
            catch( IllegalStateException expected ) {
                assertTrue(expected.getMessage().contains(reasons[i]));
            }
            assertTrue(code._allocation.toString().contains("REJECT:"));
        }
    }
}
