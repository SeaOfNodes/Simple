package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import java.util.BitSet;
import org.junit.Test;
import static org.junit.Assert.*;

// Execution regressions are separate from the fixed spill-measurement cohorts.
public class MemoryEncodingTest {
    @Test public void testLoopLoadSearch() {
        String[] sources = {
            """
            u8[] !program=new u8[106]; u8[] !out=new u8[0];
            int sum=0;
            for(int i=0; i<arg; i++) {
                sum+=program#;
                if(i==1) out=new u8[i+1];
                else if(i==3) out=new u8[2];
            }
            return sum+out#;
            """,
            // One possibly aliasing arm must reject the unchanged-memory proof.
            """
            struct S { int x; }; S !a=new S; S !b=new S;
            a.x=10; b.x=20; S !p=a; if(arg&1) p=b;
            int sum=0;
            for(int i=0; i<6; i++) {
                if(i==2) p.x=30;
                else if(i==4) b.x=40;
                sum+=a.x;
            }
            return sum;
            """,
            // Unchanged memory does not imply an unchanged loop-carried pointer.
            """
            struct S { int x; }; S !a=new S; S !b=new S;
            a.x=10; b.x=20; S !p=a; int sum=0;
            for(int i=0; i<6; i++) {
                if(i==2) a.x=30;
                sum+=p.x;
                p=b; if(i&1) p=a;
            }
            return sum;
            """,
            // All arms fold, but their values differ: build a value Phi.
            """
            struct S { int x; }; S !a=new S; a.x=1; int sum=0;
            for(int i=0; i<6; i++) {
                sum+=a.x;
                if(i&1) a.x=2;
                else if(i==2) a.x=3;
                else a.x=4;
            }
            return sum;
            """
        };
        // Exercise both unresolved Store aliases and nested value-Phi cleanup.
        for( long seed : new long[]{0,9,126} )
            for( int p=0; p<sources.length; p++ ) {
                CodeGen code=new CodeGen(sources[p],seed,true).parse().opto();
                if( p==0 ) assertNull(code._stop.walk(n ->
                    n instanceof LoadNode ld && ld.ptr() instanceof ProjNode ptr &&
                    ptr.in(0) instanceof NewNode nn &&
                    nn.size()._type==com.seaofnodes.simple.type.TypeInteger.constant(110) ? ld : null));
                for( int arg=0; arg<7; arg++ ) {
                    long expected=switch(p) {
                    case 0 -> 106*arg+(arg>=2 ? 2 : 0);
                    case 1 -> (arg&1)==0 ? 140 : 60;
                    case 2 -> 130;
                    default -> 16;
                    };
                    assertEquals(Long.toString(expected),Eval2.eval(code,arg));
                }
            }
    }

    @Test public void testKeepReadsBeforeWrites() {
        var code = new CodeGen("""
            struct S { int x; int y; };
            S !a = new S; S !b = new S;
            a.x=arg; b.x=arg+1;
            S !p=a; S !q=b;
            if (arg<0) { p=b; q=a; }
            int v;
            if (arg>1) { v=p.x; p.x=41; p.y=5; }
            else       { v=q.x; q.x=42; }
            return v;
            """).parse().opto();
        StopNode stop = code._stop;
        assertEquals(2,countMemoryNodes(stop,LoadNode.class,new BitSet()));
        assertEquals("-1",Eval2.eval(code,-1));
        assertEquals("1",Eval2.eval(code,0));
        assertEquals("3",Eval2.eval(code,3));
    }

    private static int countMemoryNodes(Node n, Class<?> kind, BitSet seen) {
        if (n==null || seen.get(n._nid)) return 0;
        seen.set(n._nid);
        int cnt=kind.isInstance(n) ? 1 : 0;
        for (Node def : n._inputs) cnt+=countMemoryNodes(def,kind,seen);
        return cnt;
    }
}
