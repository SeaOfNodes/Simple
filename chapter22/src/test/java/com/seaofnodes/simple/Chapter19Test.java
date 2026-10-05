package com.seaofnodes.simple;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;


import com.seaofnodes.simple.codegen.CodeGen.Phase;
import com.seaofnodes.simple.codegen.CodeGen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class Chapter19Test {
    @Test public void testPrintingFunctionLookup() {
        var code = new CodeGen("return 0;").parse();
        var tfp = TypeFunPtr.make((byte)2,TypeTuple.make(TypeInteger.constant(987654)),TypeInteger.constant(123456),1L<<30);
        var con = new ConstantNode(tfp);
        var call = new CallNode(null,code._start,con,con);
        // Diagnostic lookup may construct/intern the return-erased linker key.
        con.toString();
        org.junit.Assert.assertNull(call.name());

        var fun = new FunNode(null,tfp,"printerTarget");
        fun._name = "printerTarget";
        fun.addDef(code._start);
        code.link(fun);
        var otherReturn = tfp.makeFrom(TypeInteger.constant(654321));
        org.junit.Assert.assertSame(fun,code.link(otherReturn));
        org.junit.Assert.assertTrue(con.toString().contains("printerTarget"));
        assertEquals("printerTarget",call.name());
    }


    @Test
    public void testJig() {
        CodeGen code = new CodeGen("""
return 0;
""");
        code.parse().opto().typeCheck();
        assertEquals("return 0;", code._stop.toString());
        assertEquals("0", Eval2.eval(code,  2));
    }


    @Test
    public void testString() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/stringHash.smp"));
        CodeGen code = new CodeGen(src).parse().opto().typeCheck().GCM().localSched();
        assertEquals("Stop[ return Phi(Region,._hashCode,Phi(Region,123456789,Phi(Loop,0,(.[]+((Phi_hash<<5)-Phi_hash))))); return Phi(Region,1,0,0,1); ]", code._stop.toString());
        //assertEquals("-4898613127354160978", Eval2.eval(code,  2));
    }

    @Test
    public void testBasic0() {
        CodeGen code = new CodeGen("return 0;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return 0;", code._stop.toString());
    }

    @Test
    public void testBasic1() {
        CodeGen code = new CodeGen("return arg+1;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (inc,arg);", code._stop.toString());
    }

    @Test
    public void testBasic2() {
        CodeGen code = new CodeGen("return -17;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return -17;", code._stop.toString());
    }


    @Test
    public void testBasic3() {
        CodeGen code = new CodeGen("return arg==1;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (set==,(cmp,arg));", code._stop.toString());
    }

    @Test
    public void testBasic4() {
        CodeGen code = new CodeGen("return arg<<1;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (shli,arg);", code._stop.toString());
    }

    @Test
    public void testBasic5() {
        CodeGen code = new CodeGen("return arg >> 1;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (sari,arg);", code._stop.toString());
    }

    @Test
    public void testBasic6() {
        CodeGen code = new CodeGen("return arg >>> 1;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (shri,arg);", code._stop.toString());
    }

    @Test
    public void testBasic7() {
        CodeGen code = new CodeGen("return arg / 2;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (div,arg,2);", code._stop.toString());
    }

    @Test
    public void testBasic8() {
        CodeGen code = new CodeGen("return arg * 6;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (muli,arg);", code._stop.toString());
    }

    @Test
    public void testBasic9() {
        CodeGen code = new CodeGen("return arg & 2;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (andi,arg);", code._stop.toString());
    }

    @Test
    public void testBasic10() {
        CodeGen code = new CodeGen("return arg | 2;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (ori,arg);", code._stop.toString());
    }

    @Test
    public void testBasic11() {
        CodeGen code = new CodeGen("return arg ^ 2;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (xori,arg);", code._stop.toString());
    }

    @Test
    public void testBasic12() {
        CodeGen code = new CodeGen("return arg + 2.0;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (addf,(cvtf,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testBasic13() {
        CodeGen code = new CodeGen("return arg - 2.0;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (subf,(cvtf,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testBasic14() {
        CodeGen code = new CodeGen("return arg * 2.0;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (mulf,(cvtf,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testBasic15() {
        CodeGen code = new CodeGen("return arg / 2.0;").driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (mulf,(cvtf,arg),0.5f);", code._stop.toString());
    }

    @Test
    public void testBasic16() {
        CodeGen code = new CodeGen(
"""
int arg1 =  arg + 1;
return arg1 / arg;""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (div,(inc,arg),arg);", code._stop.toString());
    }

    @Test
    public void testBasic17() {
        CodeGen code = new CodeGen(
"""
int arg1 =  arg + 1;
return arg1 * arg;
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (mul,(inc,arg),arg);", code._stop.toString());
    }

    @Test
    public void testToFloat() {
        CodeGen code = new CodeGen("""
int a = arg;
return a + 2.0;
"""
        ).driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (addf,(cvtf,arg),2.0f);", code._stop.toString());
    }

    @Test
    public void testIfStmt() {
        CodeGen code = new CodeGen(
"""
int a = 1;
if (arg == 1)
    a = arg+2;
else {
    a = arg-3;
}
return a;""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return Phi(Region,(addi,arg),(addi,arg));", code.print());
    }

    @Test
    public void testIfMerge2() {
        CodeGen code = new CodeGen(
"""
int a=arg+1;
int b=arg+2;
if( arg==1 )
    b=b+a;
else
    a=b+1;
return a+b;""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (add,(add,Phi(Region,(shli,arg),arg),arg),Phi(Region,4,5));", code.print());
    }

    @Test
    public void testLoop() {
        CodeGen code = new CodeGen(
"""
int sum=0;
for( int i=0; i<arg; i++ )
    sum += i;
return sum;""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return Phi(Loop,0,(add,Phi_sum,Phi(Loop,0,(inc,Phi_i))));", code.print());
    }

    @Test
    public void testAlloc1() {
        CodeGen code = new CodeGen(
"""
struct S { int a; !S? !c; };
return new S;""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return S;", code.print());
    }

    @Test
    public void testLea1() {
        CodeGen code = new CodeGen("int x = arg/3; return arg+x+7;");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (lea,arg,(div,arg,3));", code.print());
    }

    @Test
    public void testLea2() {
        CodeGen code = new CodeGen("int x = arg/3; return arg+x*4+7;");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (lea,arg,(div,arg,3));", code.print());
    }

    @Test
    public void testLea3() {
        CodeGen code = new CodeGen("int x = arg/3; return x*4+arg;");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return (lea,arg,(div,arg,3));", code.print());
    }

    @Test
    public void testAlloc2() {
        CodeGen code = new CodeGen("int[] !xs = new int[3]; xs[arg]=1; return xs[arg&1];");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return .[];", code.print());
    }

    @Test
    public void testAlloc3() {
        CodeGen code = new CodeGen("int[] !xs = new int[3]; xs[arg]=1; return xs[arg&1]+3;");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return .[];", code.print());
    }

    @Test
    public void testArray1() {
        CodeGen code = new CodeGen(
"""
int[] !ary = new int[arg];
// Fill [0,1,2,3,4,...]
for( int i=0; i<ary#; i++ )
    ary[i] = i;
// Fill [0,1,3,6,10,...]
for( int i=0; i<ary#-1; i++ )
    ary[i+1] += ary[i];
return ary[1] * 1000 + ary[3]; // 1 * 1000 + 6
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return .[];", code.print());
    }

    @Test
    public void testArray2() {
        CodeGen code = new CodeGen(
"""
flt[] !A = new flt[arg], !B = new flt[arg];
// Fill [0,1,2,3,4,...]
for( int i=0; i<A#; i++ )
    A[i] = i;
for( int i=0; i<A#; i++ )
    B[i] += A[i];
return 0;
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return 0;", code.print());
    }

    @Test
    public void testArray3() {
        CodeGen code = new CodeGen(
"""
byte[] !A = new byte[arg];
for( int i=0; i<A#; i++ )
    A[i]++;
return A[1];
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return .[];", code.print());
    }

    @Test
    public void testNewton() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/newtonFloat.smp"))
            + "flt farg = arg;  return test_sqrt(farg);";
        CodeGen code = new CodeGen(src).driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return Phi(Loop,(cvtf,arg),(mulf,(addf,(divf,cvtf,Phi_guess),Phi_guess),0.5f));", code.print());
    };


    @Test
    public void sieveOfEratosthenes() throws IOException {
        String src = Files.readString(Path.of("src/test/java/com/seaofnodes/simple/progs/sieve.smp"));
        CodeGen code = new CodeGen(src).driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("return [u32];", code.print());
        //assertEquals("u32[ 2,3,5,7,11,13,17,19]",Eval2.eval(code, 20));
    }


    @Test
    public void testFcn1() {
        CodeGen code = new CodeGen(
"""
val fcn = arg ? { int x -> x*x; } : { int x -> x+x; };
return fcn(2)*10 + fcn(3);
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("Stop[ return (shli,Parm_x($fun2,int)); return (mul,Parm_x($fun1,int),x); return (add,#2,(muli,#2)); ]", code.print());
    }

    @Test
    public void testFcn2() {
        CodeGen code = new CodeGen(
"""
val sq = { int x -> x*x; };
return sq(arg) + sq(3);
""");
        code.driver(Phase.LocalSched,"x86_64_v2", "SystemV");
        assertEquals("Stop[ return (mul,Parm_x(sq,int),x); return (add,#2,#2); ]", code.print());
    }

    @Test public void testSelectedMemory() {
        for( String cpu : new String[]{"x86_64_v2","riscv","arm"} )
            for( String src : new String[]{Chapter10Test.NESTED_MEMORY,Chapter16Test.CONSTRUCTOR_MEMORY,
                                          Chapter18Test.CALL_MEMORY,Chapter18Test.RECURSIVE_MEMORY} ) {
                var code = new CodeGen(src).parse().opto().typeCheck();
                var aliases = memoryPhis(code._stop);
                code.loopTree().instSelect(cpu,"SystemV");
                assertEquals(aliases,memoryPhis(code._stop));
                code.GCM().localSched();
                code._stop.walk(n -> {
                    if( !(n instanceof CFGNode) && !(n instanceof ProjNode) )
                        assertTrue(n.in(0) instanceof CFGNode);
                    if( n instanceof NewNode nn ) {
                        assertEquals(2,((TypeTuple)nn._type)._types.length);
                        assertTrue(nn.mem() instanceof MemMergeNode);
                        assertNull(nn.mem().in(1)); // Partial allocation input.
                        nn.cacheRegs(code);
                        MachNode mach = (MachNode)nn;
                        assertNull(mach.regmap(1));
                        assertNotNull(mach.regmap(2));
                        assertNotNull(mach.outregmap(0));
                        assertNull(mach.outregmap(1));
                    }
                    if( n instanceof CallNode call ) {
                        assertEquals(4,call.nIns()); // ctrl, memory, two args; direct target is embedded.
                    }
                    return null;
                });
            }
    }

    private static java.util.ArrayList<String> memoryPhis(Node stop) {
        var phis = new java.util.ArrayList<String>();
        // Count only definitions reachable from Stop, as instruction selection does.
        // A dead Phi cycle can still appear through the reverse use edges.
        var seen = new java.util.BitSet();
        var work = new java.util.ArrayList<Node>();
        work.add(stop);
        for( int i=0; i<work.size(); i++ ) {
            Node n = work.get(i);
            if( n==null || seen.get(n._nid) ) continue;
            seen.set(n._nid);
            for( Node def : n._inputs ) work.add(def);
            if( n instanceof MemPhiNode phi ) phis.add("alias:"+phi._alias);
            if( n instanceof BulkMemPhiNode phi ) phis.add("bulk:"+phi._aliases);
            assertFalse(n instanceof PhiNode && n.isMem() &&
                !(n instanceof MemPhiNode) && !(n instanceof BulkMemPhiNode) && !(n instanceof ParmNode));
        }
        java.util.Collections.sort(phis);
        return phis;
    }

    @Test public void testFoldedReadBeforeWrites() {
        var code = new CodeGen("""
            struct S { int x; };
            !S !a = new S { x=11; }; !S !b = new S { x=22; };
            !S !p = a; if( arg ) p = b;
            int before = p.x + arg;
            a.x=33; b.x=44;
            return before;
            """).parse().opto().typeCheck().loopTree().instSelect("x86_64_v2","SystemV").GCM().localSched();
        var read = (MemOpNode)code._stop.walk(n ->
            n instanceof com.seaofnodes.simple.node.cpus.x86_64_v2.AddMemX86 ? n : null);
        assertNotNull(read);
        int writes = 0;
        for( Node use : read.antiDeps() )
            if( use instanceof MemOpNode st && st.isMem() ) {
                assertSame(read.cfg0(),st.cfg0());
                assertTrue(st._inputs.find(read)>=0);
                assertTrue(read.cfg0()._outputs.find(read)<st.cfg0()._outputs.find(st));
                assertNull(((MachNode)st).regmap(st._inputs.find(read)));
                writes++;
            }
        assertEquals(1,writes); // The next Store follows this first clobber through memory.
    }
}
