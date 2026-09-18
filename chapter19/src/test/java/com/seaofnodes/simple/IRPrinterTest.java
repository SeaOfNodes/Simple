package com.seaofnodes.simple;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class IRPrinterTest {
    private static final String SOURCE = """
        int sum=0;
        int i=0;
        while(i<arg) {
            int j=0;
            while(j<i) { sum=sum+j; j=j+1; }
            i=i+1;
        }
        return sum;
        """;

    // No exact whitespace/node-number golden: require a stable, complete
    // listing and contiguous Loop/Phi headers, including nested loops.
    @Test public void testLoopHeadersAndReadOnlyPrinting() {
        CodeGen code = new CodeGen(SOURCE).parse();
        Node stop = code._stop;
        ArrayList<Node> nodes = inputs(stop);
        IdentityHashMap<Node,List<Node>> defs = new IdentityHashMap<>();
        IdentityHashMap<Node,List<Node>> uses = new IdentityHashMap<>();
        IdentityHashMap<Node,Type> types = new IdentityHashMap<>();
        for( Node n : nodes ) {
            defs.put(n,copy(n._inputs));
            uses.put(n,copy(n._outputs));
            types.put(n,n._type);
        }
        String printed = IRPrinter.prettyPrint(stop,999);
        assertEquals(printed,IRPrinter.prettyPrint(stop,999));
        String program = code.toString();
        assertEquals(program,code.toString());
        List<Integer> programIds = ids(program);
        assertEquals(programIds.size(),new HashSet<>(programIds).size());
        List<Integer> ids = ids(printed);
        assertEquals(ids.size(),new HashSet<>(ids).size());
        int loops = 0;
        for( Node n : nodes ) {
            assertTrue("missing node " + n._nid,ids.contains(n._nid));
            assertEquals(defs.get(n),copy(n._inputs));
            assertEquals(uses.get(n),copy(n._outputs));
            assertSame(types.get(n),n._type);
            if( !(n instanceof LoopNode) || n instanceof FunNode ) continue;
            loops++;
            ArrayList<Node> phis = new ArrayList<>();
            for( Node use : n._outputs )
                if( use instanceof PhiNode && use._inputs.get(0)==n ) phis.add(use);
            phis.sort(Comparator.comparingInt(p -> p._nid));
            int idx = ids.indexOf(n._nid);
            for( Node phi : phis ) assertEquals(Integer.valueOf(phi._nid),ids.get(++idx));
            idx = programIds.indexOf(n._nid);
            assertTrue("missing loop " + n._nid,idx>=0);
            for( Node phi : phis ) assertEquals(Integer.valueOf(phi._nid),programIds.get(++idx));
        }
        assertTrue("nested source loops must remain",loops>=2);
        // Printing must not call idepth() merely because the phase advanced.
        CodeGen.Phase phase = code._phase;
        code._phase = CodeGen.Phase.LocalSched;
        try { assertEquals(printed,IRPrinter.prettyPrint(stop,999)); }
        finally { code._phase = phase; }
    }

    @Test(timeout=5000) public void testBrokenGraphs() {
        CodeGen code = new CodeGen(SOURCE).parse();
        Node stop = code._stop;
        // No entry control or backedge yet, and no reverse edge for the Phi.
        LoopNode loop = new LoopNode(null,null);
        PhiNode phi = new PhiNode("x",TypeInteger.BOT,loop,null,null);
        loop._outputs.clear();
        List<Integer> header = ids(IRPrinter.prettyPrint(phi,0));
        assertEquals(Arrays.asList(loop._nid,phi._nid),header);
        // An orphan projection's label may itself dereference missing inputs.
        ProjNode proj = new ProjNode(null,0,"broken");
        assertTrue(ids(IRPrinter.prettyPrint(proj,0)).contains(proj._nid));
        // A data cycle with no roots must terminate and print both nodes once.
        AddNode a = new AddNode(null,null);
        AddNode b = new AddNode(a,null);
        a._inputs.set(1,b);
        a._outputs.add(null); // keep edge
        List<Integer> cycle = ids(IRPrinter.prettyPrint(a,999));
        assertEquals(2,cycle.size());
        assertTrue(cycle.contains(a._nid));
        assertTrue(cycle.contains(b._nid));
        a._inputs.clear();
        assertTrue(ids(IRPrinter.prettyPrint(a,999)).contains(a._nid));
        assertEquals("",IRPrinter.prettyPrint(null,999));
    }

    @Test public void testProjectionOrderAndSpacing() {
        new CodeGen("return 0;").parse();
        IfNode multi = new IfNode(null,null);
        ProjNode p2 = new ProjNode(multi,2,"two");
        ProjNode pm = new ProjNode(multi,-1,"minus");
        CProjNode p1 = new CProjNode(multi,1,"one");
        ProjNode p0 = new ProjNode(multi,0,"zero");
        String printed = IRPrinter.prettyPrint(p2,0);
        assertEquals(Arrays.asList(multi._nid,pm._nid,p0._nid,p1._nid,p2._nid),ids(printed));
        String[] lines = printed.split("\n");
        int first = line(lines,multi);
        assertTrue(first>0 && lines[first-1].isBlank());
        assertEquals(first+1,line(lines,pm));
        assertEquals(first+2,line(lines,p0));
        assertEquals(first+3,line(lines,p1));
        assertEquals(first+4,line(lines,p2));
    }

    @Test public void testNestedLoopRpo() {
        new CodeGen("return 0;").parse();
        LoopNode outer = new LoopNode(null,null);
        LoopNode inner = new LoopNode(null,outer);
        PhiNode op = new PhiNode("outer",TypeInteger.BOT,outer,null,null);
        PhiNode ip = new PhiNode("inner",TypeInteger.BOT,inner,null,null);
        AddNode innerValue = new AddNode(ip,null);
        AddNode outerValue = new AddNode(ip,op);
        ip._inputs.set(2,innerValue);
        op._inputs.set(2,outerValue);
        IfNode branch = new IfNode(inner,ip);
        CProjNode left  = new CProjNode(branch,0,"left");
        CProjNode right = new CProjNode(branch,1,"right");
        RegionNode join = new RegionNode((Parser.Lexer)null,null,left,right);
        IfNode innerLatch = new IfNode(join,ip);
        CProjNode innerBack = new CProjNode(innerLatch,0,"innerBack");
        CProjNode innerExit = new CProjNode(innerLatch,1,"innerExit");
        inner._inputs.set(2,innerBack);
        IfNode outerLatch = new IfNode(innerExit,op);
        CProjNode outerBack = new CProjNode(outerLatch,0,"outerBack");
        CProjNode outerExit = new CProjNode(outerLatch,1,"outerExit");
        outer._inputs.set(2,outerBack);
        IfNode after = new IfNode(outerExit,op);

        // Raw input edits deliberately leave incomplete reverse edges.
        String printed = IRPrinter.prettyPrint(after,999);
        List<Integer> ids = ids(printed);
        assertEquals(ids.size(),new HashSet<>(ids).size());
        before(ids,outer,inner);
        before(ids,inner,branch);
        before(ids,branch,join);
        before(ids,join,innerLatch);
        before(ids,innerLatch,outerLatch);
        before(ids,innerValue,outerValue);
        before(ids,innerLatch,outerValue);
        before(ids,outerValue,outerLatch);
        before(ids,outerLatch,after);
        assertEquals(Integer.valueOf(ip._nid),ids.get(ids.indexOf(inner._nid)+1));
        assertEquals(Integer.valueOf(op._nid),ids.get(ids.indexOf(outer._nid)+1));
        String[] lines = printed.split("\n");
        for( Node cfg : new Node[]{outer,inner,branch,join,innerLatch,outerLatch,after} ) {
            int idx=line(lines,cfg);
            assertTrue("blank before CFG " + cfg._nid,idx>0 && lines[idx-1].isBlank());
        }
        assertEquals(line(lines,branch)+1,line(lines,left));
        assertEquals(line(lines,branch)+2,line(lines,right));
    }

    private static void before(List<Integer> ids, Node a, Node b) {
        assertTrue("missing " + a._nid,ids.contains(a._nid));
        assertTrue("missing " + b._nid,ids.contains(b._nid));
        assertTrue(a._nid + " before " + b._nid,ids.indexOf(a._nid)<ids.indexOf(b._nid));
    }

    private static int line(String[] lines, Node n) {
        for( int i=0; i<lines.length; i++ )
            if( lines[i].trim().startsWith(n._nid+" ") ) return i;
        fail("missing line " + n._nid);
        return -1;
    }

    private static List<Integer> ids(String text) {
        ArrayList<Integer> ids = new ArrayList<>();
        for( String line : text.split("\n") ) {
            String s = line.trim();
            if( !s.isEmpty() && Character.isDigit(s.charAt(0)) )
                ids.add(Integer.valueOf(s.split("\\s+",2)[0]));
        }
        return ids;
    }

    private static ArrayList<Node> inputs(Node root) {
        ArrayList<Node> nodes = new ArrayList<>();
        IdentityHashMap<Node,Boolean> seen = new IdentityHashMap<>();
        nodes.add(root);
        seen.put(root,Boolean.TRUE);
        for( int i=0; i<nodes.size(); i++ ) {
            Node n = nodes.get(i);
            if( n._inputs!=null ) for( Node def : n._inputs )
                if( def!=null && seen.put(def,Boolean.TRUE)==null ) nodes.add(def);
        }
        return nodes;
    }

    private static List<Node> copy(Iterable<Node> edges) {
        ArrayList<Node> copy = new ArrayList<>();
        if( edges!=null ) for( Node n : edges ) copy.add(n);
        return copy;
    }
}
