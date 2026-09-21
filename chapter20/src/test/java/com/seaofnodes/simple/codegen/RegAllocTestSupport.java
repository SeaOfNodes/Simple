package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.x86_64_v2.x86_64_v2;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.ByteArrayOutputStream;
import static org.junit.Assert.*;

// Small scheduled machine graphs isolate register constraints from peepholes.
public class RegAllocTestSupport {
    private static final RegMask A = new RegMask(0), B = new RegMask(1);
    private static class Op extends MachConcreteNode {
        final RegMask _def, _use, _kill;
        final boolean _clone;
        Op(RegMask def, RegMask use, RegMask kill, boolean clone, Node... ins) {
            super(ins); _def=def; _use=use; _kill=kill; _clone=clone; _type=TypeInteger.BOT;
        }
        @Override public String op() { return "testOp"; }
        @Override public RegMask outregmap() { return _def; }
        @Override public RegMask regmap(int i) { return _use; }
        @Override public RegMask killmap() { return _kill; }
        @Override public boolean isClone() { return _clone; }
        @Override public Node copy() { return new Op(_def,_use,_kill,_clone,(Node)null); }
        @Override public int encoding(ByteArrayOutputStream bytes) { throw new AssertionError(); }
    }
    private static CodeGen graph() {
        CodeGen code = new CodeGen("");
        code._mach = new x86_64_v2();
        code._cfg.add(code._start);
        return code;
    }

    public static void uncoloredFixedNeighbor() throws Exception {
        CodeGen code = graph();
        RegAlloc alloc = new RegAlloc(code);
        Op def = new Op(new RegMask(3L),null,null,false,code._start);
        LRG lrg = alloc.newLRG(def);
        LRG neighbor = new LRG((short)99);
        neighbor._mask = A;
        lrg.addNeighbor(neighbor); // Uncolored, but its only possible color is A.
        var bias = IFG.class.getDeclaredMethod("biasColorNeighbors",RegAlloc.class,Node.class,RegMask.class);
        bias.setAccessible(true);
        assertEquals((short)1,bias.invoke(null,alloc,def,new RegMaskRW(3L,0L)));
    }

    public static void masks() {
        for( int reg : new int[]{0,63,64,65,127} ) {
            RegMask mask = new RegMask(reg);
            assertEquals(reg,mask.firstReg());
            assertEquals(1,mask.size());
        }
        assertEquals(-1,new RegMask(0L).firstReg());
        assertFalse(new RegMask(0L).size1());
        RegMask boundary = new RegMask(Long.MIN_VALUE,Long.MIN_VALUE|1);
        assertEquals(64,boundary.nextReg((short)63));
        assertEquals(127,boundary.nextReg((short)64));
        assertEquals(-1,boundary.nextReg((short)127));
    }

    public static void union() {
        CodeGen code = graph();
        Op use = new Op(null,B,null,false,code._start);
        LRG a = new LRG((short)1), b = new LRG((short)2);
        a._mask = new RegMask(3L);
        b._mask = new RegMask(6L);
        b.machUse(use,(short)2,true);
        LRG both = a.union(b);
        assertNotNull(both._mask);
        assertEquals(1,both._mask.firstReg());
        assertEquals(1,both._mask.size());
        assertSame(use,both._machUse);
        assertEquals(2,both._uidx);
    }

    public static void killWithoutResult() {
        CodeGen code = graph();
        Op def = new Op(A,null,null,false,code._start);
        new Op(null,null,A,false,code._start); // Clobbers A, produces no value.
        new Op(null,A,null,false,code._start,def);
        RegAlloc alloc = new RegAlloc(code);
        assertTrue(BuildLRG.run(0,alloc));
        assertFalse("A live value cannot survive a register kill",IFG.build(0,alloc));
    }

    public static void nullUseMask() {
        CodeGen code = graph();
        Op def = new Op(A,null,null,false,code._start);
        new Op(null,null,null,false,code._start,def); // Scheduling dependency only.
        RegAlloc alloc = new RegAlloc(code);
        assertTrue(BuildLRG.run(0,alloc));
        assertTrue(IFG.build(0,alloc));
    }

    public static void cloneRegisterClass() {
        for( RegMask required : new RegMask[]{B,new RegMask(6L)} ) {
            CodeGen code = graph();
            Op def = new Op(A,null,null,true,code._start);
            Op use = new Op(null,required,null,false,code._start,def);
            Op use2 = new Op(null,required,null,false,code._start,def);
            RegAlloc alloc = new RegAlloc(code);
            alloc.regAlloc(); // Re-cloning an A-only value cannot satisfy B/C uses.
            assertEquals(0,alloc.regnum(def));
            assertTrue(required.test(alloc.regnum(use.in(1))));
            assertTrue(required.test(alloc.regnum(use2.in(1))));
            assertTrue(use.in(1) instanceof SplitNode);
        }
    }

    public static void commutativePhi() {
        CodeGen code = graph();
        Op a = new Op(A,null,null,false,code._start);
        Op b = new Op(A,null,null,false,code._start);
        Op add = new Op(A,A,null,false,code._start,a,b) {
            @Override public boolean commutes() { return true; }
        };
        RegionNode region = new RegionNode(null,code._start,code._start);
        new PhiNode("sum",TypeInteger.BOT,region,add,a);
        code._cfg.add(region);
        assertTrue(BuildLRG.run(0,new RegAlloc(code)));
    }

    public static void checkRegisters(CodeGen code) {
        assertTrue(code._regAlloc.verifyFunctionLocalEdges());
        for( CFGNode bb : code._cfg )
            for( Node n : bb._outputs ) {
                if( n instanceof MachNode mach ) {
                    if( code._regAlloc.regnum(n)>=0 && mach.outregmap()!=null )
                        assertTrue(n.getClass().getSimpleName()+"#"+n._nid,code._regAlloc.regnum(n)>=0 && mach.outregmap().test(code._regAlloc.regnum(n)));
                    for( int i=n instanceof ParmNode ? 2 : 1; i<n.nIns(); i++ )
                        if( n.in(i)!=null && code._regAlloc.regnum(n.in(i))>=0 && mach.regmap(i)!=null )
                            assertTrue(n.getClass().getSimpleName()+"#"+n._nid+" input "+i,code._regAlloc.regnum(n.in(i))>=0 && mach.regmap(i).test(code._regAlloc.regnum(n.in(i))));
                    if( mach.twoAddress()!=0 )
                        assertEquals(code._regAlloc.regnum(n),code._regAlloc.regnum(n.in(mach.twoAddress())));
                }
                if( n instanceof PhiNode && code._regAlloc.regnum(n)>=0 )
                    for( int i=n instanceof ParmNode ? 2 : 1; i<n.nIns(); i++ )
                        assertEquals(code._regAlloc.regnum(n),code._regAlloc.regnum(n.in(i)));
            }
    }

    public static void copyClobber() throws Exception {
        for( boolean killed : new boolean[]{false,true} ) {
            CodeGen code = graph();
            RegAlloc alloc = new RegAlloc(code);
            Op def = new Op(A,null,null,false,code._start);
            alloc.newLRG(def)._reg=0;
            SplitNode hi = code._mach.split("test",(byte)0,null);
            hi.setDef(0,code._start); hi.setDef(1,def);
            alloc.newLRG(hi)._reg=1;
            new Op(null,null,killed ? A : null,false,code._start);
            SplitNode lo = code._mach.split("test",(byte)0,null);
            lo.setDef(0,code._start); lo.setDef(1,hi);
            alloc.newLRG(lo)._reg=0;
            Op use = new Op(null,A,null,false,code._start,lo);
            var post = RegAlloc.class.getDeclaredMethod("postColor");
            post.setAccessible(true); post.invoke(alloc);
            assertSame(killed ? lo : def,use.in(1));
        }
        CodeGen pre = graph();
        RegAlloc uncolored = new RegAlloc(pre);
        Op def0 = new Op(A,null,null,false,pre._start);
        uncolored.newLRG(def0)._mask=A;
        Op fixed = new Op(A,null,null,false,pre._start);
        uncolored.newLRG(fixed)._mask=A;
        SplitNode copy = pre._mach.split("test",(byte)0,null);
        copy.setDef(0,pre._start); copy.setDef(1,def0);
        assertFalse("A fixed definition clobbers even before coloring",uncolored.sameBlockNoClobber(copy));
    }

}
