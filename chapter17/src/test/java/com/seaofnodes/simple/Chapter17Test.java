package com.seaofnodes.simple;

import com.seaofnodes.simple.evaluator.Evaluator;
import com.seaofnodes.simple.node.StopNode;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Chapter17Test {

    @Test
    public void testJig() {
        Parser parser = new Parser("""
return 3.14;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 3.14;", stop.toString());
        assertEquals(3.14, Evaluator.evaluate(stop,  0));
    }

    @Test
    public void testInc0() {
        Parser parser = new Parser("""
return arg++;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return arg;", stop.toString());
        assertEquals(0L, Evaluator.evaluate(stop, 0));
    }

    @Test
    public void testInc1() {
        Parser parser = new Parser("""
return arg+++arg++;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return ((arg*2)+1);", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop, 0));
    }

    @Test
    public void testInc2() {
        Parser parser = new Parser("""
//   -(arg--)-(arg--)
return -arg---arg--;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return (-((arg*2)+-1));", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop, 0));
    }

    @Test
    public void testInc3() {
        Parser parser = new Parser("""
int[] xs = new int[arg];
xs[0]++;
xs[1]++;
return xs[0];
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 1;", stop.toString());
        assertEquals(1L, Evaluator.evaluate(stop, 2));
    }

    @Test
    public void testInc4() {
        Parser parser = new Parser("""
u8[] xs = new u8[1];
xs[0]--;
return xs[0];
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 255;", stop.toString());
        assertEquals(255L, Evaluator.evaluate(stop, 2));
    }

    @Test
    public void testInc5() {
        Parser parser = new Parser("""
struct S { u16 x; };
S s = new S;
s.x--;
return s.x;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return 65535;", stop.toString());
        assertEquals(65535L, Evaluator.evaluate(stop, 2));
    }

    @Test
    public void testLinkedList2() {
        Parser parser = new Parser("""
struct LLI { LLI? next; int i; };
LLI? head = null;
while( arg-- )
    head = new LLI { next=head; i=arg; };
int sum=0;
while( head ) {
    sum = sum + head.i;
    head = head.next;
}
return sum;
""");
        StopNode stop = parser.parse().iterate();
        assertEquals("return Phi(Loop35,0,(Phi_sum+.i));", stop.toString());
        assertEquals(45L, Evaluator.evaluate(stop, 10));
    }

}
