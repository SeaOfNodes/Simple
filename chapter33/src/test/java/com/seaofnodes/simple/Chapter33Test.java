package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeFunPtr;
import com.seaofnodes.simple.type.TypeMemPtr;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.TypeMem;
import com.seaofnodes.simple.type.TypeScalar;
import com.seaofnodes.simple.type.TypeTuple;
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

    // Until ^ and .del() have a checked source contract, a native call supplies
    // the real control/memory boundary at which we ask the ownership question.
    // These programs are analyzed only: retire is not linked or executed.
    private static ArrayDelete deletion(CodeGen code) {
        var calls=new ArrayList<CallNode>();
        code._start.walk(n -> { if( n instanceof CallNode call ) calls.add(call); return null; });
        CallNode checkpoint=null;
        for( CallNode call : calls )
            if( call.external() ) {
                assertNull("Expected one deletion checkpoint",checkpoint);
                checkpoint=call;
            }
        assertNotNull("Missing deletion checkpoint",checkpoint);
        return code._allocation.checkArrayDelete(checkpoint,3);
    }

    @Test public void testArrayDeleteLocalAndBorrow() {
        CodeGen code=compile("""
            val work_noInline={i64 n ->
                { u8[] -> i64 } retire="C";
                val copy_noInline={u8[] dst, u8[] src, i64 len ->
                    for(i64 i=0; i<len; i++) dst[i]=src[i];
                    return len;
                };
                val old=new u8[n], next=new u8[n*2];
                copy_noInline(next,old,n);
                retire(old);
                return next[0];
            };
            return work_noInline(arg);
            """);
        ArrayDelete check=deletion(code);
        assertTrue(check.toString(),check.proven());
    }

    private static final String BUFFER = "struct Buffer { u8[] !buf=new u8[8]; };\n";

    @Test public void testArrayDeleteFieldReplacement() {
        CodeGen code=compile(BUFFER+"""
            val resize_noInline={!Buffer b, i64 n ->
                { u8[] -> i64 } retire="C";
                val copy_noInline={u8[] dst, u8[] src, i64 len ->
                    for(i64 i=0; i<len; i++) dst[i]=src[i];
                    return len;
                };
                val old=b.buf;
                val next=new u8[n];
                copy_noInline(next,old,old#);
                b.buf=next;
                retire(old);
                return b.buf[0];
            };
            return resize_noInline(new Buffer,arg);
            """);
        ArrayDelete check=deletion(code);
        assertNull(check.toString(),check._reject);
        // Local kills are proved, caller uniqueness is NOT silently assumed.
        assertNotNull(check._unique);
        assertFalse(check.proven());
        assertTrue(check.toString().contains("requires unique entry"));
    }

    @Test public void testArrayDeleteSurvivingAlias() {
        for( String alias : new String[]{"old", "n ? old : new u8[8]"} ) {
            CodeGen code=compile("""
                val work_noInline={i64 n ->
                    { u8[] -> i64 } retire="C";
                    val old=new u8[n];
                    val saved=
                """+alias+";"+"""
                    retire(old);
                    return saved[0];
                };
                return work_noInline(arg);
                """);
            ArrayDelete check=deletion(code);
            assertNotNull(check.toString(),check._reject);
            assertTrue(check.toString(),check._reject.contains("survives deletion"));
        }
    }

    @Test public void testArrayDeleteConditionalKill() {
        for( boolean both : new boolean[]{false,true} ) {
            CodeGen code=compile(BUFFER+"""
                val resize_noInline={!Buffer b, i64 n ->
                    { u8[] -> i64 } retire="C";
                    val old=b.buf;
                    if(n) b.buf=new u8[16];
                """+(both ? "else b.buf=new u8[32];" : "")+"""
                    retire(old);
                    return b.buf[0];
                };
                return resize_noInline(new Buffer,arg);
                """);
            ArrayDelete check=deletion(code);
            if( both ) assertNull(check.toString(),check._reject);
            else {
                assertNotNull(check.toString(),check._reject);
                assertTrue(check.toString(),check._reject.contains("still holds the array"));
            }
        }
    }

    @Test public void testArrayDeleteFreshOwnerAndStoredAlias() {
        for( boolean saved : new boolean[]{false,true} ) {
            CodeGen code=compile("""
                struct Buffer { u8[] !buf=new u8[8]; u8[]? !saved=null; };
                val work_noInline={i64 n ->
                    { u8[] -> i64 } retire="C";
                    val b=new Buffer;
                    val old=new u8[n];
                    b.buf=old;
                """+(saved ? "b.saved=old;" : "")+"""
                    b.buf=new u8[n*2];
                    retire(old);
                    return b;
                };
                return work_noInline(arg);
                """);
            ArrayDelete check=deletion(code);
            if( !saved ) assertTrue(check.toString(),check.proven());
            else {
                assertNotNull(check.toString(),check._reject);
                assertTrue(check.toString(),check._reject.contains("still holds the array"));
            }
        }
    }

    @Test public void testArrayDeleteAliasedOwner() {
        CodeGen code=compile(BUFFER+"""
            val resize_noInline={!Buffer b, !Buffer other, i64 n ->
                { u8[] -> i64 } retire="C";
                val old=b.buf;
                val saved=other.buf;
                b.buf=new u8[n];
                retire(old);
                return saved[0];
            };
            val b=new Buffer;
            return resize_noInline(b,b,arg);
            """);
        ArrayDelete check=deletion(code);
        assertNotNull(check.toString(),check._reject);
        assertTrue(check.toString(),check._reject.contains("survives deletion"));
    }

    @Test public void testArrayDeleteRetainingHelper() {
        CodeGen code=compile("""
            struct Buffer { u8[] !buf=new u8[8]; u8[]? !saved=null; };
            val work_noInline={i64 n ->
                { u8[] -> i64 } retire="C";
                val retain_noInline={!Buffer b -> b.saved=b.buf; return 0; };
                val b=new Buffer;
                val old=new u8[n];
                b.buf=old;
                retain_noInline(b);
                b.buf=new u8[n*2];
                retire(old);
                return b;
            };
            return work_noInline(arg);
            """);
        ArrayDelete check=deletion(code);
        assertNotNull(check.toString(),check._reject);
        assertTrue(check.toString(),check._reject.contains("field .saved still holds the array"));
    }

    @Test public void testArrayDeleteRawBorrowAndRepeatedDelete() {
        CodeGen code=compile("""
            val work_noInline={i64 n ->
                { u8[] -> i64 } retire="C";
                val old=new u8[n];
                i64 address=old;
                retire(old);
                return address;
            };
            return work_noInline(arg);
            """);
        ArrayDelete check=deletion(code);
        assertNotNull(check.toString(),check._reject);
        assertTrue(check.toString(),check._reject.contains("raw-address borrow"));

        code=compile("""
            val work_noInline={i64 n ->
                { u8[] -> i64 } retire="C";
                val old=new u8[8];
                while(n>0) { retire(old); n--; }
                return n;
            };
            return work_noInline(arg);
            """);
        check=deletion(code);
        assertNotNull(check.toString(),check._reject);
        assertTrue(check.toString(),check._reject.contains("backedge"));
    }

    @Test public void testArrayDeleteRehash() {
        CodeGen code=compile("""
            struct Table { i64[] !keys=new i64[8]; };
            val rehash_noInline={!Table table ->
                { i64[] -> i64 } retire="C";
                val old=table.keys;
                val next=new i64[old#*2];
                for(i64 i=0; i<old#; i++) {
                    val key=old[i];
                    if(key) {
                        i64 j=key & (next#-1);
                        while(next[j]) j=(j+1) & (next#-1);
                        next[j]=key;
                    }
                }
                table.keys=next;
                retire(old);
                return table.keys#;
            };
            return rehash_noInline(new Table);
            """);
        ArrayDelete check=deletion(code);
        assertNull(check.toString(),check._reject);
        assertNotNull(check._unique);
        assertFalse(check.proven());
    }

    @Test public void testArrayDeleteCopiedElementReference() {
        CodeGen code=compile("""
            struct Item { i64 x=42; };
            val work_noInline={i64 n ->
                { Item?[] -> i64 } retire="C";
                val item=new Item;
                val old=new Item?[n];
                old[0]=item;
                val saved=old[0];
                retire(old);
                return saved ? saved.x : 0;
            };
            return work_noInline(arg);
            """);
        ArrayDelete check=deletion(code);
        assertTrue(check.toString(),check.proven());
    }

    @Test public void testArrayDeleteConstructorRetention() {
        CodeGen code=compile("""
            struct Box {
                u8[] buf;
                new Box={u8[] buf -> self.buf=buf;};
            };
            val work_noInline={i64 n ->
                { u8[] -> i64 } retire="C";
                val old=new u8[n];
                val box=new Box(old);
                retire(old);
                return box;
            };
            return work_noInline(arg);
            """);
        ArrayDelete check=deletion(code);
        assertNotNull(check.toString(),check._reject);
        assertFalse(check.proven());
    }

    @Test public void testArrayDeleteSourceSyntax() {
        for( String allocation : new String[]{"new u8[^arg]", "new u8[^]{1,2,3}", "new u8[^8]{int i -> i;}"} ) {
            CodeGen code=compile("{ u8[^] a="+allocation+"; a.del(); return 42; }");
            code._allocation.requireSupported();
            assertEquals(1,code._allocation._deletes.size());
            assertTrue(code._allocation._deletes.get(0).toString(),code._allocation._deletes.get(0).proven());
        }
        CodeGen code=compile("""
            val work_noInline={i64 n ->
                val read_noInline={u8[~] a -> a[0];};
                val a=new u8[^n];
                val answer=read_noInline(a);
                a.del();
                return answer;
            };
            return work_noInline(arg);
            """);
        assertTrue(code._allocation._deletes.get(0).toString(),code._allocation._deletes.get(0).proven());
        code._allocation.summary("work_noInline").requireSupported();
    }

    private static void deleteError(String source, String message) {
        try { compile("{"+source+"}"); fail("Expected deletion rejection"); }
        catch( com.seaofnodes.simple.parser.Parser.ParseException e ) {
            assertTrue(e.getMessage(),e.getMessage().contains(message));
        }
    }

    @Test public void testArrayDeleteSourceErrors() {
        deleteError("val a=new u8[8]; a.del(); return 0;","requires a non-null [^]");
        deleteError("u8[^] a=new u8[8]; return a#;","not of declared type");
        deleteError("val a=new u8[^8]; a.del(); return a[0];","survives deletion");
        deleteError("val a=new u8[^8]; a.del(); a.del(); return 0;","after deletion");
        deleteError("val a=new u8[^8]; return a.del;","must be called directly");
        deleteError("val a=new u8[^8]; a.del=0; return 0;","not a writable field");
        compile("""
            val consume_noInline={u8[^] a -> a.del(); return 0;};
            return consume_noInline(new u8[^8]);
            """);
    }

    @Test public void testArrayDeleteExecutionNotImplemented() {
        CodeGen code=compile("{ val a=new u8[^8]; a.del(); return 0; }");
        try { Eval2.eval(code,0); fail("Deletion must not silently become a no-op"); }
        catch( UnsupportedOperationException e ) {
            assertTrue(e.getMessage(),e.getMessage().contains("runtime recycling is not implemented"));
        }
    }

    @Test public void testArrayDeleteSerializedBody() throws Exception {
        Path build=Path.of("build/objs/separate");
        Files.createDirectories(build);
        Path lib=Files.createTempDirectory(build,"array-delete-");
        new CodeGen(null,lib.toString(),null,"DeleteLib","""
            val work={i64 n -> val a=new u8[^n]; a.del(); return n;};
            """,123,true,TypeInteger.BOT).driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        var paths=new Ary<>(new String[]{lib.toString()});
        CodeGen code=new CodeGen(null,lib.toString(),paths,"DeleteClient",
            "return DeleteLib.work(arg);",123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.TypeCheck);
        assertFalse(code._allocation._deletes.isEmpty());
        for( ArrayDelete deletion : code._allocation._deletes ) assertTrue(deletion.toString(),deletion.proven());
    }

    @Test public void testArrayDeleteSourceField() {
        CodeGen code=compile("""
            struct Buffer { u8[^] !buf=new u8[^8]; };
            val work_noInline={i64 n ->
                val b=new Buffer;
                val old=b.buf;
                b.buf=new u8[^n];
                old.del();
                return b;
            };
            return work_noInline(arg);
            """);
        assertEquals(1,code._allocation._deletes.size());
        assertTrue(code._allocation._deletes.get(0).toString(),code._allocation._deletes.get(0).proven());
    }

    @Test public void testArrayDeleteForwardMember() {
        CodeGen code=compile("""
            val work_noInline={ -> val b=make(); val old=b.buf; b.buf=new u8[^16]; old.del(); return b; };
            struct Buffer { u8[^] !buf=new u8[^8]; };
            val make={ -> new Buffer; };
            return work_noInline();
            """);
        assertFalse(code._allocation._deletes.isEmpty());
        for( ArrayDelete deletion : code._allocation._deletes ) assertTrue(deletion.toString(),deletion.proven());
        // A forward receiver with a user-defined del is an ordinary method.
        code=compile("""
            val work_noInline={ -> make().del(); };
            struct Thing { val del={ -> 17; }; };
            val make={ -> new Thing; };
            return work_noInline();
            """);
        assertTrue(code._allocation._deletes.isEmpty());
        assertEquals("17",Eval2.eval(code,0));
    }

    // Read the same remote bound as ary.capacity, without that helper or
    // ary.copy obscuring the initializer's effect on the deletion proof.
    @Test public void testArrayDeleteAryu8IntegerInit() {
        var paths=new Ary<>(new String[]{TestC.RELEASE_SYS_DIR});
        CodeGen code=new CodeGen(null,null,paths,"Test","""
            { i32 -> i64 } _exit="C";
            struct aryu8 {
                u32 !len = 0;
                u8[^] !buf = new u8[^1];
                val _grow_noInline = { u32 len2 ->
                    if( len+len2 <= buf# ) return self;
                    i64 newlen = buf#*2;
                    // Triggers <clinit> for sys.integer
                    if( newlen > sys.integer.MAX_U32 ) _exit(1);
                    val old = buf;
                    val next = new u8[^newlen];
                    for(u32 i=0; i<len; i++) next[i] = old[i];
                    buf = next;
                    old.del();
                    return self;
                };
            };
            u32 extra=arg;
            return new aryu8()._grow_noInline(extra);
            """,126,true,TypeInteger.BOT).driver(CodeGen.Phase.Allocation);
        assertEquals(2,code._allocation._deletes.size());
        ArrayDelete deletion=code._allocation._deletes.get(0);
        assertTrue(code._allocation._deletes.get(1).toString(),code._allocation._deletes.get(1).proven());
        FunNode integerInit=code.link(code._compunits.get("sys/integer"));
        SummaryNode summary=integerInit.summary();
        // Break here to inspect the imported summary and the deletion memory.
        assertNotNull("Exercise a loaded summary, not a parsed initializer body",summary);
        assertFalse(summary._effects._referenceWrites);
        assertNotSame(TypeMem.BOT,summary._state.mem());
        assertNull(deletion.toString(),deletion._reject);
        assertNotNull(deletion.toString(),deletion._unique);
        for( FunNode fun : code._linker )
            assertTrue("Unreferenced DateTime must not survive Opto",
                       fun==null || fun.isDead() || fun._name==null || !fun._name.contains("sys.time.DateTime"));
        var grow=code._allocation.summary("_grow_noInline").requireSupported();
        boolean dynamic=false;
        for( Allocation.Site site : grow._sites )
            if( site._allocation!=null && site._bytes<0 ) {
                assertEquals("buddy",site.destination());
                assertFalse(grow._scratch.contains(site));
                dynamic=true;
            }
        assertTrue(dynamic);
        var inits=new ArrayList<CLInitNode>();
        code._start.walk(n -> { if( n instanceof CLInitNode init ) inits.add(init); return null; });
        boolean found=false;
        for( CLInitNode init : inits )
            if( init.cu()==integerInit._compunit ) {
                assertFalse(init.bypass());
                found=true;
            }
        assertTrue("The remote initialization guard must remain",found);
        code.driver(CodeGen.Phase.TypeCheck);
    }

    @Test public void testExitCallEnd() {
        CodeGen code=compile("""
            { i32 -> i64 } _exit="C";
            if(arg) return _exit(1);
            return 17;
            """);
        var ends=new ArrayList<CallEndNode>();
        code._start.walk(n -> { if( n instanceof CallEndNode end ) ends.add(end); return null; });
        assertEquals(1,ends.size());
        assertEquals(TypeTuple.make(Type.CONTROL,TypeMem.TOP,TypeScalar.TOP),ends.get(0)._type);
        // The concrete call survives scheduling and encoding; do not execute it.
        code.driver(CodeGen.Phase.Encoding,TestC.CPU_PORT,TestC.CALL_CONVENTION);
        var calls=new ArrayList<CallNode>();
        code._start.walk(n -> { if( n instanceof CallNode call ) calls.add(call); return null; });
        assertEquals(1,calls.size());
        assertTrue(calls.get(0) instanceof MachNode);
    }

    @Test public void testExitMixedTargets() {
        CodeGen code=compile("""
            { i32 -> i64 } _exit="C", other="C";
            return (arg ? _exit : other)(1);
            """);
        var ends=new ArrayList<CallEndNode>();
        code._start.walk(n -> { if( n instanceof CallEndNode end ) ends.add(end); return null; });
        assertEquals(1,ends.size());
        TypeTuple state=(TypeTuple)ends.get(0)._type;
        assertEquals(Type.CONTROL,state.ctl());
        assertNotEquals(TypeMem.TOP,state._types[1]);
        assertEquals(TypeInteger.BOT,state.ret());
        // A Simple function named _exit has ordinary return semantics.
        code=compile("val _exit={i64 x -> x+1;}; return _exit(arg);");
        assertEquals("18",Eval2.eval(code,17));
    }

    @Test public void testAllocationBuddyLoop() {
        // Two iterations' blocks may coexist; neither occupies iteration scratch.
        CodeGen code=compile("""
            val work_noInline={i64 n ->
                u8[^] !last=new u8[^1];
                i64 total=0;
                while(n>0) {
                    val next=new u8[^n];
                    total+=last#;
                    last=next;
                    n--;
                }
                return total+last#;
            };
            return work_noInline(arg);
            """);
        var sum=code._allocation.summary("work_noInline").requireSupported();
        boolean carried=false;
        for( Allocation.Summary loop : sum._frames )
            for( Allocation.Site site : loop._continues )
                if( site._allocation!=null && site._frame==loop ) {
                    assertTrue(site.buddy());
                    assertFalse(loop._scratch.contains(site));
                    carried=true;
                }
        assertTrue(carried);
    }

    @Test public void testSummaryOwnEffects() throws Exception {
        Path base=Files.createDirectories(Path.of("build/objs/summary-effects"));
        Path lib=Files.createTempDirectory(base,"own-");
        new CodeGen(null,lib.toString(),null,"Effects","""
            u8[] !saved=new u8[0];
            val retain_noInline={u8[] xs -> saved=xs; return 0;};
            val _callback_noInline={i64 n -> n+1;};
            val _unused_noInline={i64 n -> n+2;};
            val clean_noInline={i64 n -> n+3;};
            val publish_noInline={ ->
                { {i64 -> i64} -> i64 } publish="C";
                publish(_callback_noInline);
                while(true) {}
            };
            """,123,true,TypeInteger.BOT).driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        var paths=new Ary<>(new String[]{lib.toString()});
        CodeGen code=new CodeGen(null,null,paths,"Clean",
            "return Effects.clean_noInline(arg);",123,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
        for( FunNode fun : code._linker )
            if( fun!=null && !fun.isDead() && fun._name!=null ) {
                assertFalse(fun._name,fun._name.contains("publish_noInline"));
                assertFalse(fun._name,fun._name.contains("_callback_noInline"));
                assertFalse(fun._name,fun._name.contains("_unused_noInline"));
                if( fun._name.endsWith("clean_noInline") ) {
                    assertNotNull(fun.summary());
                    assertSame(com.seaofnodes.simple.type.XInt.EMPTY,fun.summary()._effects._writes);
                }
            }
        assertEquals("20",Eval2.eval(code,17));

        code=new CodeGen(null,null,paths,"Retain",
            "Effects.retain_noInline(new u8[3]); return Effects.saved#;",123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.TypeCheck);
        boolean retained=false;
        for( FunNode fun : code._linker )
            if( fun!=null && !fun.isDead() && fun._name!=null && fun._name.endsWith("retain_noInline") ) {
                assertNotNull(fun.summary());
                assertTrue(fun.summary()._effects._referenceWrites);
                assertNotSame(com.seaofnodes.simple.type.XInt.EMPTY,fun.summary()._effects._writes);
                retained=true;
            }
        assertTrue(retained);
        assertEquals("3",Eval2.eval(code,0));

        // Retention on a nonreturning path must survive saving/loading too.
        code=new CodeGen(null,null,paths,"Publish",
            "if(arg) Effects.publish_noInline(); return 7;",123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.TypeCheck);
        boolean callback=false;
        for( FunNode fun : code._linker )
            if( fun!=null && !fun.isDead() && fun._name!=null && fun._name.endsWith("_callback_noInline") ) {
                assertTrue(fun.unknownCallers());
                callback=true;
            }
        assertTrue("The native can retain the callback before the infinite loop",callback);
    }

    @Test public void testArrayDeleteAryu8Copy() {
        var paths=new Ary<>(new String[]{TestC.RELEASE_SYS_DIR});
        CodeGen code=new CodeGen(null,null,paths,"Test","""
            struct aryu8 {
                u32 !len = 0;
                u8[^] !buf = new u8[^1];
                val _grow_noInline = { u32 len2 ->
                    if( len2 <= buf# ) return self;
                    val old = buf;
                    val next = new u8[^len2];
                    sys.ary.copy(next,0,old,0,len);
                    buf = next;
                    old.del();
                    return self;
                };
            };
            u32 extra=arg;
            return new aryu8()._grow_noInline(extra);
            """,126,true,TypeInteger.BOT).driver(CodeGen.Phase.Allocation);
        assertEquals(2,code._allocation._deletes.size());
        ArrayDelete deletion=code._allocation._deletes.get(0);
        assertTrue(code._allocation._deletes.get(1).toString(),code._allocation._deletes.get(1).proven());
        FunNode copy=null;
        for( FunNode fun : code._linker )
            if( fun!=null && !fun.isDead() && "sys.ary.copy".equals(fun._name) ) copy=fun;
        assertNotNull(copy);
        CallNode copyCall=null;
        for( CallNode call : code._calls )
            if( !call.isDead() && call.fptr()._type instanceof TypeFunPtr fp &&
                fp.isConstant() && fp.fidx()==copy.sig().fidx() ) copyCall=call;
        assertNotNull(copyCall);
        var effects=code._effects.get(copy);
        var contract=code._allocation.summary("sys.ary.copy").requireSupported();
        // Break here: the unloaded copy's contract returns arg3 (dst).
        assertNotNull(copy.summary());
        assertNotNull(copy.summary()._allocation);
        assertEquals(1,contract._returns.size());
        assertEquals(3,contract._returns.iterator().next()._arg);
        assertFalse(effects._referenceWrites);
        assertTrue(copy.ret().expr()._type instanceof TypeMemPtr);
        assertNull(deletion.toString(),deletion._reject);
        assertNotNull(deletion._unique);
        code.driver(CodeGen.Phase.TypeCheck);
    }

    @Test public void testAllocationSerializedContracts() throws Exception {
        Path base=Files.createDirectories(Path.of("build/objs/allocation-contracts"));
        Path lib=Files.createTempDirectory(base,"graph-");
        var paths=new Ary<>(new String[]{lib.toString()});
        new CodeGen(null,lib.toString(),null,"Contracts",THING+HOLDER+"""
            val make_noInline={i64 n -> new Thing(n);};
            val holder_noInline={ -> new Holder;};
            val field_noInline={Holder h -> h.c;};
            val put_noInline={ !Holder h, Thing t -> h.c=t; return 0;};
            val choose_noInline={Thing a, Thing b, i64 n -> n ? a : b;};
            struct Link { Link? !next; i64 x; new Link={i64 n -> x=n;}; };
            val cycle_noInline={i64 n ->
                val a=new Link(n), b=new Link(n+1);
                a.next=b; b.next=a; return a;
            };
            val unsupported_noInline={u32 n -> new u8[n];};
            val buddy_noInline={u32 n -> new u8[^n];};
            """,123,true,TypeInteger.BOT).driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        CodeGen code=new CodeGen(null,null,paths,"Use","""
            val work_noInline={i64 n ->
                val h=Contracts.holder_noInline();
                val a=Contracts.make_noInline(n), b=Contracts.make_noInline(n+1);
                Contracts.put_noInline(h,a);
                return Contracts.choose_noInline(Contracts.field_noInline(h),b,n);
            };
            return work_noInline(arg).x + Contracts.cycle_noInline(arg).x;
            """,123,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
        for( String name : new String[]{"make_noInline","field_noInline","put_noInline","choose_noInline","cycle_noInline"} ) {
            var sum=code._allocation.summary(name).requireSupported();
            assertNotNull("Contract must work without loading "+name,sum._fun.summary());
        }
        var make=code._allocation.summary("make_noInline");
        var made=make._returns.iterator().next();
        assertTrue(made._fresh);
        assertNull(made._allocation);
        assertTrue(made._bytes>0);
        assertEquals("arg3.c",code._allocation.summary("field_noInline")._returns.iterator().next()._name);
        assertEquals(2,code._allocation.summary("choose_noInline")._returns.size());
        assertEquals(4,code._allocation.summary("put_noInline")._retained.iterator().next()._arg);
        var cycle=code._allocation.summary("cycle_noInline");
        var a=cycle._returns.iterator().next();
        var b=a._fields.get("next").iterator().next();
        assertTrue(b._fields.get("next").contains(a));
        var work=code._allocation.summary("work_noInline").requireSupported();
        int copies=0;
        for( Allocation.Site site : work._sites )
            if( site._fresh && site._name.endsWith("/"+made._name) ) {
                assertTrue(work._returns.contains(site));
                copies++;
            }
        assertEquals("Each call imports distinct fresh storage",2,copies);
        assertEquals("34",Eval2.eval(code,17));

        // Contracts composed from imported contracts survive another export.
        new CodeGen(null,lib.toString(),paths,"Bridge",
            "val forward_noInline={i64 n -> Contracts.make_noInline(n);};",123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        code=new CodeGen(null,null,paths,"UseBridge","return Bridge.forward_noInline(arg).x;",123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.TypeCheck);
        var forward=code._allocation.summary("forward_noInline").requireSupported();
        assertNotNull(forward._fun.summary());
        assertTrue(forward._returns.iterator().next()._fresh);
        assertEquals(made._bytes,forward._returns.iterator().next()._bytes);
        assertEquals("19",Eval2.eval(code,19));

        code=new CodeGen(null,null,paths,"Unsupported","u32 n=arg; return Contracts.unsupported_noInline(n)#;",123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.Allocation);
        var unsupported=code._allocation.summary("unsupported_noInline");
        assertNotNull(unsupported._fun.summary());
        assertFalse(unsupported.supported());
        assertTrue(unsupported._reject,unsupported._reject.contains("variable-size allocation"));

        code=new CodeGen(null,null,paths,"Buddy","u32 n=arg; return Contracts.buddy_noInline(n)#;",123,true,TypeInteger.BOT)
            .driver(CodeGen.Phase.TypeCheck);
        var buddy=code._allocation.summary("buddy_noInline").requireSupported();
        assertNotNull(buddy._fun.summary());
        var block=buddy._returns.iterator().next();
        assertTrue(block._fresh);
        assertTrue(block.buddy());
        assertEquals(-1,block._bytes);
    }

    @Test public void testArrayDeleteImportedReturnedAlias() throws Exception {
        Path base=Files.createDirectories(Path.of("build/objs/allocation-contracts"));
        Path lib=Files.createTempDirectory(base,"borrow-");
        var paths=new Ary<>(new String[]{lib.toString()});
        new CodeGen(null,lib.toString(),null,"Borrow","val identity_noInline={u8[] xs -> xs;};",123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        for( boolean after : new boolean[]{false,true} ) {
            String source="{ val a=new u8[^8]; val b=Borrow.identity_noInline(a); "+
                (after ? "a.del(); return b[0]; }" : "val x=b[0]; a.del(); return x; }");
            CodeGen code=new CodeGen(null,null,paths,"Use",source,123,true,TypeInteger.BOT)
                .driver(CodeGen.Phase.Allocation);
            var contract=code._allocation.summary("identity_noInline").requireSupported();
            assertNotNull(contract._fun.summary());
            ArrayDelete deletion=code._allocation._deletes.get(0);
            if( after ) {
                assertNotNull(deletion.toString(),deletion._reject);
                assertTrue(deletion._reject,deletion._reject.contains("survives deletion"));
            } else {
                assertTrue(deletion.toString(),deletion.proven());
                code.driver(CodeGen.Phase.TypeCheck);
            }
        }
    }

    private static final String OWNED_BUFFER="""
        struct Buffer {
            u8[^] !buf = new u8[^1];
            val grow_noInline = { u32 n ->
                if( n <= buf# ) return 0;
                val old = buf;
                buf = new u8[^n];
                old.del();
                return 0;
            };
        };
        """;

    @Test public void testArrayConsumeCallers() {
        compile(OWNED_BUFFER.replace("return 0;","return self;")+"""
            { val v=new Buffer; return v.grow_noInline(8).buf#; }
            """);
        for( String body : new String[]{
                "val v=new Buffer; v.grow_noInline(8); return v.buf#;",
                "val v=new Buffer; val w=v; v.grow_noInline(8); return w.buf#;",
                "val v=new Buffer; val saved=v.buf; val x=saved[0]; v.grow_noInline(8); return x;"} ) {
            CodeGen code=compile(OWNED_BUFFER+"{"+body+"}");
            boolean required=false, checked=false;
            for( ArrayDelete deletion : code._allocation._deletes ) {
                assertNull(deletion.toString(),deletion._reject);
                if( deletion._requirement!=null ) {
                    assertEquals("arg2.buf",deletion._requirement.toString()); required=true;
                } else { assertTrue(deletion.proven()); checked=true; }
            }
            assertTrue(required); assertTrue(checked);
        }
        for( String body : new String[]{
                "val v=new Buffer; val saved=v.buf; v.grow_noInline(8); return saved[0];",
                "val v=new Buffer; val saved=v.buf; v.grow_noInline(8); return saved#;",
                "val v=new Buffer; val saved=v.buf; v.grow_noInline(8); saved[0]=1; return 0;"} )
            deleteError(OWNED_BUFFER+"{"+body+"}","deletion");
    }

    @Test public void testArrayConsumeForwarding() {
        CodeGen code=compile(OWNED_BUFFER+"""
            val forward_noInline={ !Buffer v,u32 n -> v.grow_noInline(n);};
            { val v=new Buffer; forward_noInline(v,8); return v.buf#; }
            """);
        assertEquals(List.of(new ArrayDelete.Consume(3,List.of("buf"))),
                     code._allocation.summary("forward_noInline")._consumes);
        deleteError(OWNED_BUFFER+"""
            val forward_noInline={ !Buffer v,u32 n -> v.grow_noInline(n);};
            { val v=new Buffer; val saved=v.buf; forward_noInline(v,8); return saved[0]; }
            ""","deletion");
        String nested=OWNED_BUFFER+"""
            struct Outer {!Buffer !b; new Outer={!Buffer b -> self.b=b;};};
            val nested_noInline={ !Outer o -> o.b.grow_noInline(8);};
            """;
        code=compile(nested+"{val v=new Buffer; val o=new Outer(v); nested_noInline(o); return o.b.buf#;}");
        assertEquals(List.of(new ArrayDelete.Consume(3,List.of("b","buf"))),
                     code._allocation.summary("nested_noInline")._consumes);
        deleteError(nested+"{val v=new Buffer; val o=new Outer(v); val saved=v.buf; nested_noInline(o); return saved[0];}","deletion");

        String redirected=OWNED_BUFFER+"""
            struct Outer {!Buffer !b; new Outer={!Buffer b -> self.b=b;};};
            val redirect_noInline={ !Outer o,!Buffer b -> o.b=b; o.b.grow_noInline(8);};
            """;
        code=compile(redirected+"{val a=new Buffer,b=new Buffer; val o=new Outer(a); redirect_noInline(o,b); return o.b.buf#;}");
        assertEquals(List.of(new ArrayDelete.Consume(4,List.of("buf"))),
                     code._allocation.summary("redirect_noInline")._consumes);
        deleteError(redirected+"{val a=new Buffer,b=new Buffer; val o=new Outer(a); val saved=b.buf; redirect_noInline(o,b); return saved[0];}","deletion");
    }

    @Test public void testArrayConsumeStoredAliases() {
        String saved="struct Saved {u8[]? !a;};";
        deleteError(OWNED_BUFFER+saved+"""
            { val v=new Buffer; val saved=new Saved; saved.a=v.buf;
              v.grow_noInline(8); return saved.a==null; }
            ""","still holds the array");
        deleteError(OWNED_BUFFER+saved+"""
            { val v=new Buffer; val saved=new Saved; saved.a=v.buf;
              if(arg) saved.a=null;
              v.grow_noInline(8); return saved.a==null; }
            ""","still holds the array");
        compile(OWNED_BUFFER+saved+"""
            { val v=new Buffer; val saved=new Saved; saved.a=v.buf;
              saved.a=null; v.grow_noInline(8); return v.buf#; }
            """);
        // Aliasing two formal parameters must not evade the callee's check.
        deleteError(OWNED_BUFFER+"""
            val bad_noInline={ !Buffer v,u8[] saved -> v.grow_noInline(8); return saved[0];};
            { val v=new Buffer; return bad_noInline(v,v.buf); }
            ""","deletion");
        deleteError(OWNED_BUFFER+"""
            val consume_noInline={u8[^] a -> a.del(); return 0;};
            { val v=new Buffer; consume_noInline(v.buf); return v.buf#; }
            ""","still holds the array");
    }

    @Test public void testArrayConsumeIndirect() {
        String functions="""
            val consume_noInline={u8[^] a -> a.del(); return 0;};
            val leave_noInline={u8[^] a -> a#;};
            """;
        compile(functions+"{val a=new u8[^8]; return (arg ? consume_noInline : leave_noInline)(a);}");
        deleteError(functions+"""
            {val a=new u8[^8]; (arg ? consume_noInline : leave_noInline)(a); return a[0];}
            ""","deletion");
        deleteError("""
            val recurse_noInline={u8[^] a,i64 n -> if(n) return recurse_noInline(a,n-1); a.del(); return 0;};
            return recurse_noInline(new u8[^8],arg);
            ""","recursive consuming");
        deleteError(functions+"""
            val invoke_noInline={ {u8[^] -> i64} f,u8[^] a -> f(a);};
            return invoke_noInline(consume_noInline,new u8[^8]);
            ""","ownership contract");
        deleteError(functions+"""
            struct Handler { val f=consume_noInline; };
            { {Handler -> i64} invoke="C";
              return invoke(new Handler); }
            ""","Consuming callback");
    }

    @Test public void testArrayConsumeImported() throws Exception {
        Path base=Files.createDirectories(Path.of("build/objs/ownership"));
        Path lib=Files.createTempDirectory(base,"consume-");
        var paths=new Ary<>(new String[]{lib.toString()});
        new CodeGen(null,lib.toString(),null,"Types","struct Buffer {u8[^] !buf=new u8[^1];};",123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        new CodeGen(null,lib.toString(),paths,"Grow","""
            val grow_noInline={ !Types.Buffer v,u32 n ->
                if(n<=v.buf#) return 0;
                val old=v.buf; v.buf=new u8[^n]; old.del(); return 0;
            };
            """,123,true,TypeInteger.BOT).driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        new CodeGen(null,lib.toString(),paths,"Forward","""
            val forward_noInline={ !Types.Buffer v,u32 n -> Grow.grow_noInline(v,n);};
            """,123,true,TypeInteger.BOT).driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        for( int variant=0; variant<4; variant++ ) {
            boolean alias=variant==1;
            String grow=variant==3 ? "Forward.forward_noInline(v,8); Forward.forward_noInline(v,16);" :
                        variant==2 ? "for(u32 i=2; i<arg; i++) Forward.forward_noInline(v,i);" :
                                    "Forward.forward_noInline(v,8);";
            CodeGen code=new CodeGen(null,null,paths,"Use","""
                { val v=new Types.Buffer;
                """+(alias ? "val saved=v.buf;" : "")+grow+" return "+
                (alias ? "saved[0];}" : "v.buf#;}"),123,true,TypeInteger.BOT).driver(CodeGen.Phase.Allocation);
            var sum=code._allocation.summary("forward_noInline");
            assertNotNull("Use the serialized requirement without loading the body",sum._fun.summary());
            assertEquals(List.of(new ArrayDelete.Consume(3,List.of("buf"))),sum._consumes);
            if( alias ) {
                try { code.driver(CodeGen.Phase.TypeCheck); fail("Saved alias must reject"); }
                catch( com.seaofnodes.simple.parser.Parser.ParseException e ) {
                    assertTrue(e.getMessage(),e.getMessage().contains("deletion"));
                }
            } else {
                for( ArrayDelete check : code._allocation._deletes ) assertTrue(check.toString(),check.proven());
                code.driver(CodeGen.Phase.TypeCheck);
            }
        }
    }

    @Test public void testArrayConsumeGrowLoop() {
        // Reduced from parseAryI64: nums.add(...) repeatedly calls _grow,
        // which may replace/delete the backing buffer on each iteration.
        CodeGen code=new CodeGen(OWNED_BUFFER+"""
            {
                val v=new Buffer;
                u32 n=arg;
                for(u32 i=2; i<n; i++) v.grow_noInline(i);
                return v.buf#;
            }
            """).driver(CodeGen.Phase.Allocation);
        ArrayDelete repeated=null;
        for( ArrayDelete check : code._allocation._deletes )
            if( !Intrinsic.arrayDelete(check._call) ) repeated=check;
        assertNotNull("Exercise the repeated consuming call",repeated);
        CallNode growCall=repeated._call;
        FunNode grow=code.link((TypeFunPtr)growCall.fptr()._type);
        var contract=code._allocation.summary(grow._name);
        // Break here: code, repeated, growCall, grow, and contract are available.
        assertEquals(List.of(new ArrayDelete.Consume(2,List.of("buf"))),contract._consumes);
        assertTrue(repeated.toString(),repeated.proven());
        code.driver(CodeGen.Phase.TypeCheck);
    }

    @Test public void testArrayConsumeLoopBackedges() {
        compile(OWNED_BUFFER+"""
            val many_noInline={ !Buffer v,u32 n ->
                val same=v;
                for(u32 i=2; i<n; i++) same.grow_noInline(i);
                return v.buf#;
            };
            u32 n=arg;
            return many_noInline(new Buffer,n);
            """);
        compile(OWNED_BUFFER+"""
            { val v=new Buffer;
              u32 n=arg;
              for(u32 i=2; i<n; i++) {
                  val before=v.buf#;
                  v.grow_noInline(i);
                  if(before>4) break;
              }
              return v.buf#;
            }
            """);
        compile(OWNED_BUFFER+"""
            { val v=new Buffer;
              u32 n=arg;
              for(u32 i=2; i<n; i++)
                  for(u32 j=2; j<i; j++) v.grow_noInline(j);
              return v.buf#;
            }
            """);
        // Each iteration gets its own allocation. Repeating delete on one
        // allocation instead must fail, even without any other array accesses.
        compile("{ for(int i=0; i<arg; i++) { val a=new u8[^8]; a.del(); } return 0; }");
        deleteError("val a=new u8[^8]; for(int i=0; i<arg; i++) a.del(); return 0;","backedge");

        deleteError(OWNED_BUFFER+"""
            val v=new Buffer;
            val saved=v.buf;
            u32 n=arg;
            for(u32 i=2; i<n; i++) v.grow_noInline(i);
            return saved;
            ""","backedge");

        deleteError(OWNED_BUFFER+"""
            val v=new Buffer;
            val saved=v.buf;
            u32 n=arg;
            int sum=0;
            for(u32 i=2; i<n; i++) {
                sum+=saved[0];
                v.grow_noInline(i);
            }
            return sum;
            ""","backedge");
        // A previous iteration's local is still an alias, not a fresh reload.
        deleteError(OWNED_BUFFER+"""
            val v=new Buffer;
            u8[^]? !saved=null;
            u32 n=arg;
            int sum=0;
            for(u32 i=2; i<n; i++) {
                if(saved) sum+=saved[0];
                saved=v.buf;
                v.grow_noInline(i);
            }
            return sum;
            ""","backedge");
        // Conservatively reject copies of the replacement across the boundary.
        // The SSA case could pass with a proof that its last use precedes the
        // next grow; that ordering is not yet an iteration-entry contract.
        deleteError(OWNED_BUFFER+"""
            val v=new Buffer;
            u8[^]? !saved=null;
            u32 n=arg;
            int sum=0;
            for(u32 i=2; i<n; i++) {
                if(saved) sum+=saved[0];
                v.grow_noInline(i);
                saved=v.buf;
            }
            return sum;
            ""","backedge");
        deleteError(OWNED_BUFFER+"""
            struct Saved { u8[^]? !a=null; };
            val v=new Buffer;
            val saved=new Saved;
            u32 n=arg;
            for(u32 i=2; i<n; i++) {
                v.grow_noInline(i);
                saved.a=v.buf;
            }
            return saved.a ? saved.a# : 0;
            ""","backedge");
        deleteError(OWNED_BUFFER+"""
            struct Saved { u8[^]? !a=null; };
            val save_noInline={ !Saved saved,!Buffer v -> saved.a=v.buf; return 0; };
            val v=new Buffer;
            val saved=new Saved;
            u32 n=arg;
            for(u32 i=2; i<n; i++) {
                v.grow_noInline(i);
                save_noInline(saved,v);
            }
            return saved.a ? saved.a# : 0;
            ""","backedge");
        // A consuming contract describes the entry buffer. It must not hide
        // the callee publishing a new alias of its replacement before return.
        deleteError(OWNED_BUFFER.replace("u8[^] !buf = new u8[^1];",
                                        "u8[^] !buf = new u8[^1]; u8[^]? !saved=null;")
                                .replace("old.del();","old.del(); saved=buf;")+"""
            val v=new Buffer;
            u32 n=arg;
            for(u32 i=2; i<n; i++) v.grow_noInline(i);
            return v.saved ? v.saved# : 0;
            ""","backedge");
    }
    @Test public void testArrayConsumeTwoGrows() {
        // Reduced from text.string's padding, text, and trailing-padding grows.
        CodeGen code=new CodeGen(OWNED_BUFFER+"""
            {
                val v=new Buffer;
                v.grow_noInline(8);
                v.grow_noInline(16);
                return v.buf#;
            }
            """).driver(CodeGen.Phase.Allocation);
        ArrayDelete first=null, second=null;
        for( ArrayDelete check : code._allocation._deletes ) {
            if( Intrinsic.arrayDelete(check._call) ) continue;
            if( first==null ) first=check;
            else if( check._call.dominates(first._call,null) ) { second=first; first=check; }
            else second=check;
        }
        assertNotNull(first);
        assertNotNull(second);
        // Break here: code, first, and second expose both caller checkpoints.
        assertTrue(first.toString(),first.proven());
        assertTrue(second.toString(),second.proven());
        code.driver(CodeGen.Phase.TypeCheck);
    }

    @Test public void testArrayConsumeComposedCalls() {
        compile(OWNED_BUFFER+"""
            { val v=new Buffer;
              if(arg) v.grow_noInline(8);
              v.grow_noInline(16);
              return v.buf#;
            }
            """);
        compile(OWNED_BUFFER+"""
            { val v=new Buffer; u32 n=arg;
              for(u32 i=2; i<n; i++) {
                  v.grow_noInline(i);
                  v.grow_noInline(n);
              }
              return v.buf#;
            }
            """);
        compile(OWNED_BUFFER.replace("u8[^] !buf = new u8[^1];","""
            u8[^] !buf;
            new Buffer={u8[^] initial -> buf=initial;};
            """)+"""
            { val v=new Buffer(new u8[^1]);
              v.grow_noInline(8); v.grow_noInline(16); return v.buf#;
            }
            """);
        String append=OWNED_BUFFER+"""
            val append_noInline={ !Buffer v,u8[~] xs ->
                v.grow_noInline(8); return xs[0]+v.buf#;
            };
            """;
        compile(append+"{val v=new Buffer; return append_noInline(v,\"hello\");}");
        deleteError(append+"{val v=new Buffer; return append_noInline(v,v.buf);}","aliases the array");
        // Distinct wrapper formals can still name the same wrapper. A saved
        // field load cannot inherit the direct-array-parameter disjointness.
        deleteError(OWNED_BUFFER+"""
            val peek_noInline={ !Buffer a,!Buffer b ->
                val saved=a.buf; b.grow_noInline(8); return saved[0];
            };
            { val v=new Buffer; return peek_noInline(v,v); }
            ""","deletion");
        for( boolean between : new boolean[]{false,true} )
            deleteError(OWNED_BUFFER+"{val v=new Buffer;"+
                (between ? "v.grow_noInline(8); val saved=v.buf;" : "val saved=v.buf; v.grow_noInline(8);")+
                "v.grow_noInline(16); return saved[0];}","deletion");
        deleteError(OWNED_BUFFER+"""
            struct Saved {u8[^]? !a=null;};
            { val v=new Buffer; val s=new Saved; u32 n=arg;
              for(u32 i=2; i<n; i++) {
                  v.grow_noInline(i); v.grow_noInline(n); s.a=v.buf;
              }
              return s.a ? s.a# : 0;
            }
            ""","backedge");
        compile("""
            struct Saved {u8[^]? !a=null;};
            val save_noInline={ !Saved s,u8[^] a -> s.a=a; return 0;};
            { val s=new Saved; val a=new u8[^8];
              save_noInline(s,a); s.a=null; a.del(); return 0;
            }
            """);
        try {
            compile("""
                u8[^]? !saved=null;
                val save_noInline={u8[^] a -> saved=a; return 0;};
                {val a=new u8[^8]; save_noInline(a); a.del(); return saved ? saved# : 0;}
                """);
            fail("Global retained alias must reject");
        } catch( com.seaofnodes.simple.parser.Parser.ParseException e ) {
            // The helper's global initialization remains an opaque effect.
            assertTrue(e.getMessage(),e.getMessage().contains("unknown reference effects"));
        }
    }

    @Test public void testArrayConsumeImportedOpaqueEffects() throws Exception {
        Path base=Files.createDirectories(Path.of("build/objs/ownership"));
        Path lib=Files.createTempDirectory(base,"opaque-");
        var paths=new Ary<>(new String[]{lib.toString()});
        new CodeGen(null,lib.toString(),null,"Types","struct Saved {i64 !address=0;};",123,true,TypeInteger.BOT)
            .driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        new CodeGen(null,lib.toString(),paths,"Opaque","""
            val save_noInline={ !Types.Saved s,u8[] a -> s.address=a; return 0;};
            """,123,true,TypeInteger.BOT).driver(TestC.CPU_PORT,TestC.CALL_CONVENTION,false,false);
        CodeGen code=new CodeGen(null,null,paths,"Use","""
            { val s=new Types.Saved; val a=new u8[^8];
              Opaque.save_noInline(s,a); a.del(); return s.address;
            }
            """,123,true,TypeInteger.BOT).driver(CodeGen.Phase.Allocation);
        assertNotNull(code._allocation.summary("save_noInline")._fun.summary());
        try { code.driver(CodeGen.Phase.TypeCheck); fail("Raw store must remain opaque after loading its summary"); }
        catch( com.seaofnodes.simple.parser.Parser.ParseException e ) {
            assertTrue(e.getMessage(),e.getMessage().contains("unknown reference effects"));
        }
    }
}
