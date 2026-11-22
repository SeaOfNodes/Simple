package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import java.nio.file.Files;
import java.nio.file.Path;
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
        assertTrue(dump.indexOf("+-- fill_noInline\n") < dump.indexOf("+-- make_noInline\n"));
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
        assertTrue(borrow.toString().contains("choose_noInline"));

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
        assertTrue(cycle.toString().contains("cycle_noInline"));
        // Compiling cycle reset and recycled borrow's types. Its debugger string
        // must neither crash on cleared types nor print their reused contents.
        assertEquals("IR unavailable: shared types were reset after this compilation.",borrow.toString());
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
            "val bad_noInline={ i64 n -> new int[n]; }; return bad_noInline(arg);",
            "{ i64 -> int[] } native=\"C\"; val bad_noInline={ i64 n -> native(n); }; return bad_noInline(arg);"
        };
        String[] reasons={"recursive", "variable-size", "native result"};
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

    // A borrowed child is a separate origin. Returning it through helpers must
    // retain the Thing, not the temporary Holder used to reach it.
    @Test public void testAllocationReturnField() {
        CodeGen code=compile(THING+HOLDER+"""
            val get_noInline = { Holder h -> h.c; };
            val forward_noInline = { Holder h -> get_noInline(h); };
            val work_noInline={ i64 n ->
                val h = new Holder;
                h.c = new Thing(n);
                return forward_noInline(h);
            };
            return read_noInline(work_noInline(arg));
            """);
        for( String name : new String[]{"get_noInline","forward_noInline"} ) {
            var sum=code._allocation.summary(name).requireSupported();
            assertEquals(1,sum._returns.size());
            var child=sum._returns.iterator().next();
            assertEquals("arg3.c",child._name);
            assertEquals("result & arg3.c",child.destination());
            for( var site : sum._sites )
                if( site._arg==3 ) assertEquals("arg3",site.destination());
        }
        var work=code._allocation.summary("work_noInline");
        assertEquals("frame",allocations(work,"Holder").get(0).destination());
        assertEquals(2,allocations(work,"Thing").size()); // weak update retains the default too
        for( var site : allocations(work,"Thing") ) assertEquals("result",site.destination());
        assertEquals("17",Eval2.eval(code,17));
    }

    // Retention through a loaded reference composes across a wrapper. The
    // source holder stays local even though its child escapes to the argument.
    @Test public void testAllocationCopyField() {
        CodeGen code=compile(THING+HOLDER+"""
            val copy_noInline={ !Holder dst, Holder src -> dst.c=src.c; return 0; };
            val forward_noInline={ !Holder dst, Holder src -> copy_noInline(dst,src); };
            val work_noInline={ !Holder dst, i64 n ->
                val src=new Holder;
                src.c=new Thing(n);
                return forward_noInline(dst,src);
            };
            val dst=new Holder;
            work_noInline(dst,arg);
            return dst.c.x;
            """);
        for( String name : new String[]{"copy_noInline","forward_noInline"} ) {
            var sum=code._allocation.summary(name).requireSupported();
            boolean found=false;
            for( var site : sum._sites ) {
                if( site._name.equals("arg4.c") ) {
                    found=true;
                    assertEquals("arg3 & arg4.c",site.destination());
                }
                if( site._arg==4 ) assertEquals("arg4",site.destination());
            }
            assertTrue(found);
        }
        var work=code._allocation.summary("work_noInline");
        assertEquals("frame",allocations(work,"Holder").get(0).destination());
        assertEquals(2,allocations(work,"Thing").size());
        for( var site : allocations(work,"Thing") ) assertEquals("arg3",site.destination());
        assertEquals("23",Eval2.eval(code,23));
    }

    // An argument's child can itself receive allocations. Its lifetime is a
    // distinct destination, resolved to the actual Holder at the next call.
    @Test public void testAllocationStoreThroughField() {
        CodeGen code=compile(THING+HOLDER+"""
            struct Outer { !Holder h=new Holder; };
            val fill_noInline={ !Outer o, i64 n -> o.h.c=new Thing(n); return n; };
            val get_noInline={ Outer o -> o.h.c; };
            val work_noInline={ i64 n ->
                val o=new Outer;
                fill_noInline(o,n);
                return get_noInline(o);
            };
            return read_noInline(work_noInline(arg));
            """);
        var fill=allocations(code._allocation.summary("fill_noInline"),"Thing");
        assertEquals(1,fill.size());
        assertEquals("arg3.h",fill.get(0).destination());
        var get=code._allocation.summary("get_noInline").requireSupported();
        assertEquals("arg3.h.c",get._returns.iterator().next()._name);
        var work=code._allocation.summary("work_noInline");
        assertEquals("frame",allocations(work,"Outer").get(0).destination());
        assertEquals("frame",allocations(work,"Holder").get(0).destination());
        assertEquals(2,allocations(work,"Thing").size());
        for( var site : allocations(work,"Thing") ) assertEquals("result",site.destination());
        assertEquals("31",Eval2.eval(code,31));
    }

    // A store through one formal must reach a load through another when actuals
    // alias. Keeping all fields as weak unions also covers a load before a store.
    @Test public void testAllocationAliasedFieldLoad() {
        CodeGen code=compile(THING+HOLDER+"""
            val fill_noInline={ !Holder a, Holder b, i64 n -> a.c=new Thing(n); return b.c; };
            val same_noInline={ i64 n ->
                val h=new Holder;
                return fill_noInline(h,h,n);
            };
            val different_noInline={ i64 n ->
                val a=new Holder, b=new Holder;
                return fill_noInline(a,b,n);
            };
            return read_noInline(same_noInline(arg))+read_noInline(different_noInline(arg));
            """);
        for( String name : new String[]{"same_noInline","different_noInline"} ) {
            var sum=code._allocation.summary(name);
            for( var site : allocations(sum,"Holder") ) assertEquals("frame",site.destination());
            int returned=0;
            for( var site : allocations(sum,"Thing") )
                if( site.destination().equals("result") ) returned++;
            assertEquals(name.equals("same_noInline") ? 2 : 1,returned);
        }
        assertEquals("41",Eval2.eval(code,41));
    }

    // Weak updates can create fictitious repeated reads. Reject this case
    // instead of building infinite p.next.next... paths or silently dropping one.
    @Test public void testAllocationCyclicFieldPaths() {
        CodeGen code=compile("""
            struct Link { Link? !next; i64 x; new Link={ i64 n -> x=n; }; };
            val skip_noInline={ !Link p ->
                val a=p.next;
                if( a ) p.next=a.next;
                return p;
            };
            val a=new Link(arg), b=new Link(arg+1);
            a.next=b;
            return skip_noInline(a).x;
            """);
        var sum=code._allocation.summary("skip_noInline");
        assertFalse(sum.supported());
        assertTrue(sum._reject,sum._reject.contains("cyclic field-path expansion"));
        assertEquals("5",Eval2.eval(code,5));
    }

    // Array indices share one conservative field. Both possible children need
    // result storage; the fixed-size backing array itself remains temporary.
    @Test public void testAllocationArrayElementOrigins() {
        CodeGen code=compile(THING+"""
            val get_noInline={ Thing?[] ts, i64 n -> ts[n&1]; };
            val work_noInline={ i64 n ->
                val ts=new Thing?[2];
                ts[0]=new Thing(n);
                ts[1]=new Thing(n+1);
                return get_noInline(ts,n);
            };
            val t=work_noInline(arg);
            return t ? t.x : -1;
            """);
        var work=code._allocation.summary("work_noInline").requireSupported();
        assertEquals(2,allocations(work,"Thing").size());
        for( var site : allocations(work,"Thing") ) assertEquals("result",site.destination());
        int arrays=0;
        for( var site : work._sites )
            if( site._allocation!=null && site._allocation._ts.isAry() ) {
                arrays++;
                assertEquals("frame",site.destination());
            }
        assertEquals(1,arrays);
        assertEquals("8",Eval2.eval(code,7));
        assertEquals("6",Eval2.eval(code,6));
    }

    // One temporary per iteration, including allocations imported from a helper.
    @Test public void testAllocationLoopTemporary() {
        CodeGen code=compile(THING+"""
            val make_noInline={ i64 n -> new Thing(n); };
            val work_noInline={ i64 n ->
                i64 s=0;
                while( n>0 ) {
                    val t=make_noInline(n);
                    s+=read_noInline(t);
                    n--;
                }
                return s;
            };
            return work_noInline(arg);
            """);
        var sites=allocations(code._allocation.summary("work_noInline"),"Thing");
        assertEquals(1,sites.size());
        var temp=sites.get(0);
        assertNotNull(temp._frame._loop);
        assertSame(temp._frame,temp._scope);
        assertEquals(temp._frame.name()+" iteration",temp.destination());
        assertTrue(temp._frame._scratch.contains(temp));
        assertFalse(temp._frame._continues.contains(temp));
        assertFalse(temp._frame._returns.contains(temp));
        assertEquals("15",Eval2.eval(code,5));
        assertEquals("0",Eval2.eval(code,0));
    }

    // A loop can retain a winner while reclaiming unrelated scratch every turn.
    // The winner is built by a factory, so its destination crosses a call too.
    @Test public void testAllocationLoopWinnerAndScratch() {
        CodeGen code=compile(THING+"""
            struct Scratch { i64 x; new Scratch={ i64 n -> x=n; }; };
            val readScratch_noInline={ Scratch s -> s.x; };
            val make_noInline={ i64 n -> new Thing(n); };
            val find_noInline={ i64 n ->
                Thing? !winner=null;
                while( n>0 ) {
                    val scratch=new Scratch(n);
                    val candidate=make_noInline(readScratch_noInline(scratch));
                    if( read_noInline(candidate)==3 ) { winner=candidate; break; }
                    n--;
                }
                return winner;
            };
            Thing? t=find_noInline(arg);
            return t ? t.x : -1;
            """);
        var sum=code._allocation.summary("find_noInline").requireSupported();
        var winner=allocations(sum,"Thing").get(0);
        var scratch=allocations(sum,"Scratch").get(0);
        assertNotNull(winner._frame._loop);
        assertSame(winner._frame,scratch._frame);
        assertEquals("result",winner.destination());
        assertNull(winner._scope);
        assertSame(scratch._frame,scratch._scope);
        assertEquals(scratch._frame.name()+" iteration",scratch.destination());
        var frame=winner._frame.requireSupported();
        assertSame(sum,frame._parent);
        assertTrue(sum._frames.contains(frame));
        assertTrue(frame._returns.contains(winner));
        assertFalse(frame._continues.contains(winner));
        assertFalse(frame._scratch.contains(winner));
        assertTrue(frame._scratch.contains(scratch));
        assertFalse(frame._entry.isEmpty());
        assertFalse(frame._next.isEmpty());
        assertFalse(frame._exit.isEmpty());
        String dump=code._allocation.toString();
        assertTrue(dump.contains(frame.name()+" winner"));
        assertEquals(dump.indexOf("\n+-- "),dump.indexOf("\n+-- class:Test.<clinit>\n"));
        int others=dump.indexOf("Other retained functions");
        assertTrue(dump.indexOf("\n+-- find_noInline\n") < others);
        assertTrue(dump.indexOf("\n+-- make_noInline\n") < others);
        assertTrue(dump.indexOf("\n+-- Test.Scratch.<new>\n") > others);
        assertEquals("3",Eval2.eval(code,5));
        assertEquals("-1",Eval2.eval(code,2));
        assertEquals("-1",Eval2.eval(code,0));
    }

    // A collection needs multiple generations of an allocation site. Neither a
    // loop-carried pointer nor a pointer hidden in pre-existing memory is a winner.
    @Test public void testAllocationLoopRetainedCandidates() {
        String[] sources={
            """
            struct Link { Link? next; i64 x; new Link={ Link? p, i64 n -> next=p; x=n; }; };
            val work_noInline={ i64 n ->
                Link? !head=null;
                while( n>0 ) { head=new Link(head,n); n--; }
                return head;
            };
            val t=work_noInline(arg);
            return t ? t.x : -1;
            """,
            THING+"""
            val work_noInline={ i64 n ->
                val ts=new Thing?[2];
                while( n>0 ) { ts[n&1]=new Thing(n); n--; }
                return ts;
            };
            val ts=work_noInline(arg);
            val t=ts[1];
            return t ? t.x : -1;
            """,
            THING+HOLDER+"""
            val fill_noInline={ !Holder h, i64 n -> h.c=new Thing(n); return n; };
            val work_noInline={ i64 n ->
                val h=new Holder;
                while( n>0 ) { fill_noInline(h,n); n--; }
                return h;
            };
            return work_noInline(arg).c.x;
            """
        };
        for( String source : sources ) {
            CodeGen code=compile(source);
            var sum=code._allocation.summary("work_noInline");
            assertFalse(sum.supported());
            assertTrue(sum._reject,sum._reject.contains("across its backedge"));
            assertEquals("1",Eval2.eval(code,3));
        }
    }

    // Publishing on the exit arm is allowed. Publishing on the backedge was
    // rejected above; both cases write through a pre-existing holder.
    @Test public void testAllocationLoopWinnerStore() {
        CodeGen code=compile(THING+HOLDER+"""
            val fill_noInline={ !Holder h, i64 n ->
                while( n>0 ) {
                    val t=new Thing(n);
                    if( read_noInline(t)==3 ) { h.c=t; break; }
                    n--;
                }
                return n;
            };
            val h=new Holder;
            fill_noInline(h,arg);
            return h.c.x;
            """);
        var sites=allocations(code._allocation.summary("fill_noInline"),"Thing");
        assertEquals(1,sites.size());
        assertEquals("arg3",sites.get(0).destination());
        assertNotNull(sites.get(0)._frame._loop);
        assertNull(sites.get(0)._scope);
        assertTrue(sites.get(0)._frame._retained.contains(sites.get(0)));
        assertEquals("3",Eval2.eval(code,5));
        assertEquals("0",Eval2.eval(code,2));
    }

    // An inner winner can still be temporary in the enclosing iteration.
    @Test public void testAllocationNestedLoopWinner() {
        CodeGen code=compile(THING+"""
            val work_noInline={ i64 n ->
                i64 s=0;
                while( n>0 ) {
                    Thing? !winner=null;
                    i64 i=3;
                    while( i>0 ) {
                        val t=new Thing(n+i);
                        if( read_noInline(t)==n+2 ) { winner=t; break; }
                        i--;
                    }
                    if( winner ) s+=read_noInline(winner);
                    n--;
                }
                return s;
            };
            return work_noInline(arg);
            """);
        var sites=allocations(code._allocation.summary("work_noInline"),"Thing");
        assertEquals(1,sites.size());
        var site=sites.get(0);
        assertNotNull(site._frame._loop);
        assertNotNull(site._scope);
        assertSame(site._frame._parent,site._scope);
        assertEquals(site._scope.name()+" iteration",site.destination());
        assertTrue(site._frame._returns.contains(site));
        assertFalse(site._frame._scratch.contains(site));
        assertTrue(site._scope._scratch.contains(site));
        assertEquals("12",Eval2.eval(code,3));
    }

    // The candidate's graph survives together, but the caller can consume that
    // result locally. The helper's winner layout must not leak into its caller.
    @Test public void testAllocationLoopWinnerGraph() {
        CodeGen code=compile(THING+"""
            struct Box { Thing child; new Box={ Thing t -> child=t; }; };
            val use_noInline={ Box b -> b.child.x; };
            val find_noInline={ i64 n ->
                while( true ) {
                    val b=new Box(new Thing(n));
                    if( use_noInline(b)==3 ) return b;
                    n--;
                }
            };
            val work_noInline={ i64 n -> use_noInline(find_noInline(n)); };
            return work_noInline(arg);
            """);
        var find=code._allocation.summary("find_noInline").requireSupported();
        for( String type : new String[]{"Thing","Box"} ) {
            var winner=allocations(find,type).get(0);
            assertEquals("result",winner.destination());
            assertNotNull(winner._frame._loop);
            assertNull(winner._scope);
            var caller=allocations(code._allocation.summary("work_noInline"),type).get(0);
            assertEquals("frame",caller.destination());
            assertSame(code._allocation.summary("work_noInline"),caller._frame);
            assertSame(caller._frame,caller._scope);
            assertTrue(caller._frame._scratch.contains(caller));
        }
        assertEquals("3",Eval2.eval(code,5));
    }

    // Continuation ports can return borrowed references into the next activation.
    // They do not require retaining any storage created by the iteration.
    @Test public void testAllocationBorrowedContinuation() {
        CodeGen code=compile(THING+"""
            val work_noInline={ Thing a, Thing b, i64 n ->
                Thing !p=a;
                i64 s=0;
                while( n>0 ) {
                    s+=read_noInline(p);
                    p=(n&1) ? a : b;
                    n--;
                }
                return s+read_noInline(p);
            };
            return work_noInline(new Thing(10),new Thing(20),arg);
            """);
        var fun=code._allocation.summary("work_noInline").requireSupported();
        assertEquals(1,fun._frames.size());
        var loop=fun._frames.get(0).requireSupported();
        assertEquals(2,loop._continues.size());
        for( var site : loop._continues ) {
            assertNull(site._allocation);
            assertTrue(site._arg==3 || site._arg==4);
            assertTrue(loop._returns.contains(site));
        }
        assertTrue(loop._scratch.isEmpty());
        assertEquals("50",Eval2.eval(code,3));
        assertEquals("10",Eval2.eval(code,0));
    }

    // Both exits clean the same scratch. The normal result here is a scalar,
    // loaded before cleanup; returning it does not retain the temporary object.
    @Test public void testAllocationScalarLoopExit() {
        CodeGen code=compile(THING+"""
            val work_noInline={ i64 n ->
                i64 result=-1;
                while( n>0 ) {
                    val t=new Thing(n);
                    val x=read_noInline(t);
                    if( n==3 ) { result=x; break; }
                    n--;
                }
                return result;
            };
            return work_noInline(arg);
            """);
        var fun=code._allocation.summary("work_noInline").requireSupported();
        var temp=allocations(fun,"Thing").get(0);
        var loop=temp._frame.requireSupported();
        assertSame(loop,temp._scope);
        assertTrue(loop._scratch.contains(temp));
        assertTrue(loop._returns.isEmpty());
        assertTrue(loop._continues.isEmpty());
        boolean scalar=false;
        for( var value : loop._exit.values() )
            if( value._type instanceof com.seaofnodes.simple.type.TypeInteger ) scalar=true;
        assertTrue(scalar);
        assertEquals("3",Eval2.eval(code,5));
        assertEquals("-1",Eval2.eval(code,2));
    }

    // Returning from an inner loop exports through both enclosing interfaces;
    // each exited frame has its own cleanup obligations.
    @Test public void testAllocationNestedFrameReturn() {
        CodeGen code=compile(THING+"""
            struct Scratch { i64 x; new Scratch={ i64 n -> x=n; }; };
            val readScratch_noInline={ Scratch s -> s.x; };
            val find_noInline={ i64 n ->
                while( n>0 ) {
                    val scratch=new Scratch(n);
                    i64 i=readScratch_noInline(scratch);
                    while( i>0 ) {
                        val candidate=new Thing(i);
                        if( read_noInline(candidate)==3 ) return candidate;
                        i--;
                    }
                    n--;
                }
                return null;
            };
            Thing? t=find_noInline(arg);
            return t ? t.x : -1;
            """);
        var fun=code._allocation.summary("find_noInline").requireSupported();
        var winner=allocations(fun,"Thing").get(0);
        var scratch=allocations(fun,"Scratch").get(0);
        var inner=winner._frame;
        var outer=scratch._frame;
        assertSame(outer,inner._parent);
        assertSame(fun,outer._parent);
        assertTrue(inner._returns.contains(winner));
        assertTrue(outer._returns.contains(winner));
        assertTrue(fun._returns.contains(winner));
        assertNull(winner._scope);
        assertSame(outer,scratch._scope);
        assertTrue(outer._scratch.contains(scratch));
        assertFalse(inner._scratch.contains(scratch));
        assertEquals("3",Eval2.eval(code,5));
        assertEquals("-1",Eval2.eval(code,2));
    }

    @Test public void testAllocationExitlessLoop() {
        CodeGen code=compile(THING+"""
            val spin_noInline={ i64 n ->
                while( true ) {
                    val t=new Thing(n);
                    n=read_noInline(t)+1;
                }
                return 7;
            };
            spin_noInline(arg);
            return 7;
            """);
        var fun=code._allocation.summary("spin_noInline").requireSupported();
        assertEquals(1,fun._frames.size());
        var loop=fun._frames.get(0).requireSupported();
        assertNotNull(loop._loop);
        assertSame(loop._loop,loop._loop._ltree._head);
        assertSame(fun,loop._parent);
        var temp=allocations(fun,"Thing").get(0);
        assertSame(loop,temp._scope);
        assertTrue(loop._scratch.contains(temp));
        assertTrue(loop._returns.isEmpty());
        assertTrue(loop._continues.isEmpty());
        // No synthetic return before TypeCheck or serialization; scheduling
        // supplies the exit later. The evaluator still cannot return.
        assertSame(com.seaofnodes.simple.type.Type.XCONTROL,fun._fun.ret().ctrl()._type);
        assertNull(Eval2.eval(code,0,10));
    }

    @Test public void testAllocationNativeBorrow() {
        CodeGen code=compile("""
            { i64 i64 -> i64 } native="C";
            val work_noInline={i64 n ->
                val a=new u8[16], b=new u8[16];
                i64 pa=a, pb=b;
                i64 p=n ? pa : pb;
                return native(p+2,8);
            };
            return work_noInline(arg);
            """);
        var fun=code._allocation.summary("work_noInline").requireSupported();
        int count=0;
        for( var site : fun._sites ) if( site._allocation!=null ) {
            count++;
            assertSame(fun,site._scope);
            assertTrue(fun._scratch.contains(site));
            assertFalse(fun._returns.contains(site));
        }
        assertEquals(2,count);
        assertTrue(code._allocation.toString().contains("native (borrow; no retain)"));
    }

    @Test public void testAllocationNativeLoopBorrow() {
        CodeGen code=compile("""
            { i64 i64 -> i64 } native="C";
            val borrow_noInline={u8[] bytes ->
                i64 p=bytes;
                return native(p,bytes#);
            };
            val work_noInline={i64 n ->
                i64 total=0;
                while(n>0) {
                    val bytes=new u8[16];
                    total+=borrow_noInline(bytes);
                    i64 p=bytes;
                    total+=native(p+1,8);
                    n--;
                }
                return total;
            };
            return work_noInline(arg);
            """);
        code._allocation.summary("borrow_noInline").requireSupported();
        var fun=code._allocation.summary("work_noInline").requireSupported();
        int count=0;
        for( var site : fun._sites ) if( site._allocation!=null ) {
            count++;
            assertNotNull(site._frame._loop);
            assertSame(site._frame,site._scope);
            assertTrue(site._scope._scratch.contains(site));
            assertFalse(site._scope._continues.contains(site));
        }
        assertEquals(1,count);
    }

    @Test public void testAllocationRawAddressBoundaries() {
        String[] bodies={
            """
            i64 p=0;
            while(n>0) {
                if(p) native(p);
                val bytes=new u8[16];
                p=bytes;
                n--;
            }
            return 0;
            """,
            "val bytes=new u8[16]; i64 p=bytes; addresses[0]=p; return native(addresses[0]);"
        };
        for( int i=0; i<bodies.length; i++ ) {
            CodeGen code=compile("{ i64 -> i64 } native=\"C\"; val addresses=new i64[1]; "+
                "val work_noInline={i64 n -> "+bodies[i]+"}; return work_noInline(arg);");
            var fun=code._allocation.summary("work_noInline").requireSupported();
            for( var site : fun._sites ) if( site._allocation!=null ) {
                // Carrying the integer across iterations does not carry its
                // backing storage. The first program uses a stale raw address.
                assertSame(site._frame,site._scope);
                assertTrue(site._scope._scratch.contains(site));
            }
        }
    }

    private static List<KeepAliveNode> keeps(CodeGen code) {
        var keeps=new ArrayList<KeepAliveNode>();
        code._start.walk(n -> { if( n instanceof KeepAliveNode k ) keeps.add(k); return null; });
        return keeps;
    }

    @Test public void testKeepAliveWrappersAndReplacement() {
        CodeGen code=compile("""
            { i64 -> i64 } native="C";
            val forward_noInline={ i64 p -> native(p); };
            val wrapper_noInline={ i64 p -> forward_noInline(p); };
            val addresses=new i64[2];
            val work_noInline={ i64 n ->
                i64 raw=new u8[16];
                addresses[0]=raw;
                raw=new u8[32];
                addresses[1]=raw;
                return wrapper_noInline(addresses[n&1]);
            };
            return work_noInline(arg);
            """);
        var fun=code._allocation.summary("work_noInline").requireSupported();
        int count=0;
        for( var site : fun._sites ) if( site._allocation!=null ) {
            count++;
            assertSame(fun,site._scope);
            assertTrue(fun._scratch.contains(site));
        }
        assertEquals(2,count);
        assertEquals(1,keeps(code).size());
        KeepAliveNode keep=keeps(code).get(0);
        assertTrue(keep.in(1) instanceof KeepAliveNode.Values);
        assertSame(keep,fun._fun.ret().ctrl());
        assertTrue(keep.in(0) instanceof CProjNode);
    }

    @Test public void testKeepAliveConditionalAndErasure() {
        CodeGen code=compile("""
            { i64 -> i64 } native="C";
            val work_noInline={ i64 n ->
                i64 raw=0;
                n && ((raw=new u8[16])!=null);
                return native(raw);
            };
            return work_noInline(arg);
            """);
        var fun=code._allocation.summary("work_noInline").requireSupported();
        assertEquals(1,keeps(code).size());
        KeepAliveNode keep=keeps(code).get(0);
        assertTrue(keep.in(1) instanceof PhiNode);
        assertSame(keep,fun._fun.ret().ctrl());
        CallNode call=((CallEndNode)keep.in(0).in(0)).call();
        Node raw=call.arg(3);
        KeepAliveNode.erase(code._start);
        assertTrue(keeps(code).isEmpty());
        assertSame(raw,call.arg(3)); // Shared ordinary Phi must survive erasure.
        assertFalse(raw.isDead());
    }

    @Test public void testKeepAliveLoopExits() {
        CodeGen code=compile("""
            { i64 -> i64 } native="C";
            val work_noInline={ i64 n ->
                i64 total=0;
                while(n>0) {
                    i64 raw=new u8[16];
                    total+=native(raw);
                    if(n==1) return total;
                    if(n==2) break;
                    n--;
                    if(n==3) continue;
                    total++;
                }
                return total;
            };
            return work_noInline(arg);
            """);
        var fun=code._allocation.summary("work_noInline").requireSupported();
        assertFalse(keeps(code).isEmpty());
        for( var site : fun._sites ) if( site._allocation!=null ) {
            assertNotNull(site._frame._loop);
            assertFalse(site._frame._continues.contains(site));
            assertNotNull(site._scope);
        }
    }

    @Test public void testKeepAliveOuterLoopScope() {
        CodeGen code=compile("""
            { i64 -> i64 } native="C";
            val work_noInline={ i64 n ->
                i64 raw=0;
                while((raw=new u8[16]) && native(raw) && n-->0) { }
                return n;
            };
            return work_noInline(arg);
            """);
        var fun=code._allocation.summary("work_noInline");
        // Predicate conversions belong to the enclosing scope; every iteration
        // adds storage to that scope, unlike a conversion in the loop body.
        assertFalse(fun.supported());
        assertTrue(fun._reject,fun._reject.contains("across its backedge"));
    }

    @Test public void testKeepAliveRawReturnAndUnused() {
        CodeGen code=compile("""
            val work_noInline={ ->
                i64 raw=new u8[16];
                return raw+17;
            };
            return work_noInline();
            """);
        var fun=code._allocation.summary("work_noInline").requireSupported();
        assertTrue(fun._returns.isEmpty()); // Returned integer has no lifetime contract.
        assertFalse(fun._scratch.isEmpty());
        assertEquals(1,keeps(code).size());
        code=compile("val work_noInline={ -> val a=new u8[16]; i64 unused=a; return 1; }; return work_noInline();");
        assertTrue(keeps(code).isEmpty());
        assertEquals("1",Eval2.eval(code,0));
    }

    @Test public void testKeepAliveImportedBody() throws Exception {
        Path build=Path.of("build/objs/separate");
        Files.createDirectories(build);
        Path lib=Files.createTempDirectory(build,"keepalive-");
        new CodeGen(null,lib.toString(),null,"Borrow","""
            { i64 -> i64 } native="C";
            val borrow={ u8[] a -> i64 raw=a; return native(raw); };
            """,123,true,TypeInteger.BOT).driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        var paths=new Ary<>(new String[]{lib.toString()});
        CodeGen code=new CodeGen(null,lib.toString(),paths,"BorrowClient",
            "return Borrow.borrow(new u8[16]);",123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.TypeCheck);
        assertFalse(keeps(code).isEmpty());
        code.driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        assertTrue(keeps(code).isEmpty());
    }

    @Test public void testKeepAliveForwardConversion() {
        CodeGen code=compile("""
            { i64 -> i64 } native="C";
            val work_noInline={ -> i64 raw=make_noInline(); return native(raw); };
            val make_noInline={ -> new u8[16]; };
            return work_noInline();
            """);
        var fun=code._allocation.summary("work_noInline").requireSupported();
        assertEquals(1,keeps(code).size());
        assertFalse(fun._scratch.isEmpty());
        assertTrue(fun._returns.isEmpty());
    }

    @Test public void testKeepAliveImplicitScopes() {
        CodeGen code=compile("""
            { i64 -> i64 } native="C";
            val work_noInline={ i64 n ->
                i64 raw=0;
                if(n) raw=new u8[16]; else raw=new u8[32];
                return native(raw);
            };
            return work_noInline(arg);
            """);
        code._allocation.summary("work_noInline").requireSupported();
        // Each unbraced arm ends its own borrowing scope. The subsequent call
        // deliberately uses stale integers; it must not move these endpoints.
        assertEquals(2,keeps(code).size());
        for( KeepAliveNode keep : keeps(code) ) {
            assertTrue(keep.in(0) instanceof CProjNode);
            assertTrue(keep.in(0).in(0) instanceof IfNode);
            boolean merges=false;
            for( Node use : keep._outputs ) if( use instanceof RegionNode ) merges=true;
            assertTrue(merges);
        }
    }

    @Test public void testKeepAliveNestedFunction() {
        CodeGen code=compile("""
            { i64 -> i64 } native="C";
            val work_noInline={ i64 n ->
                i64 raw=new u8[16];
                val helper_noInline={i64 k -> while(k>0) k--; return k;};
                helper_noInline(n);
                return native(raw);
            };
            return work_noInline(arg);
            """);
        var fun=code._allocation.summary("work_noInline").requireSupported();
        assertEquals(1,keeps(code).size());
        assertSame(keeps(code).get(0),fun._fun.ret().ctrl());
        assertFalse(fun._scratch.isEmpty());
    }
}
