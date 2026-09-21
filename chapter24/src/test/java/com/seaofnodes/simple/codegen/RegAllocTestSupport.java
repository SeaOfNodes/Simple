package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.x86_64_v2.x86_64_v2;
import com.seaofnodes.simple.type.TypeInteger;
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
        @Override public void encoding(Encoding enc) { throw new AssertionError(); }
    }
    private static CodeGen graph() {
        CodeGen code = new CodeGen("");
        code._mach = new x86_64_v2(code);
        code._cfg.add(code._start);
        return code;
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

    private static class CallOp extends CallNode implements MachNode {
        CallOp(Node ctrl, Node def) { super(null,ctrl,null,def,def); }
        @Override public RegMask regmap(int i) { return i==2 ? A : i==3 ? B : null; }
        @Override public RegMask outregmap() { return null; }
        @Override public void encoding(Encoding enc) { throw new AssertionError(); }
    }

    public static void popularUses() {
        for( int calls=0; calls<=2; calls++ ) {
            CodeGen code = graph();
            RegAlloc alloc = new RegAlloc(code);
            RegionNode block = new RegionNode(null,code._start,code._start);
            Op def = new Op(new RegMask(7L),null,null,false,block);
            LRG lrg = alloc.newLRG(def);
            lrg._machDef = def;
            lrg._mask = new RegMask(0L);
            Op ab = new Op(null,new RegMask(3L),null,false,block,def);
            Op bc = new Op(null,new RegMask(6L),null,false,block,def);
            Op a = new Op(null,A,null,false,block,def,def);
            Op b = new Op(null,B,null,false,block,def);
            Op dep = new Op(null,null,null,false,block,def);
            CallOp call = calls>0 ? new CallOp(block,def) : null;
            if( calls==2 ) new CallOp(block,def);
            boolean split = alloc.splitEmptyMaskByUse((byte)0,lrg);
            assertEquals(calls<2,split);
            if( !split ) { assertSame(def,a.in(1)); assertSame(def,b.in(1)); continue; }
            // Two shared copies, even with overlapping masks and repeated inputs.
            assertTrue(a.in(1) instanceof SplitNode);
            assertTrue(b.in(1) instanceof SplitNode);
            assertNotSame(a.in(1),b.in(1));
            assertSame(a.in(1),a.in(2));
            assertSame(b.in(1),ab.in(1));
            assertSame(b.in(1),bc.in(1));
            assertSame(def,dep.in(1)); // A scheduling dependency has no register.
            assertEquals(3,def.nOuts()); // Two copies and the dependency.
            if( call!=null ) {
                assertSame(a.in(1),call.in(2));
                assertSame(b.in(1),call.in(3));
            }
        }
    }

    // Check the scheduled graph before encoding adds untyped branches or rewrites tail calls.
    public static class CheckedCodeGen extends CodeGen {
        public CheckedCodeGen(String src) { super(src); }
        public CheckedCodeGen(String src, TypeInteger arg) { super(src,arg); }
        @Override public CodeGen regAlloc() {
            super.regAlloc();
            checkRegisters(this);
            return this;
        }
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

    public static void coldLoopSelfConflict() {
        for( boolean phiConflict : new boolean[]{false,true} ) {
            CodeGen code = graph();
            RegAlloc alloc = new RegAlloc(code);
            Op init = new Op(A,null,null,false,code._start);
            LoopNode loop = new LoopNode(null,code._start);
            PhiNode phi = new PhiNode("loop",TypeInteger.BOT,loop,init,null);
            Op back = new Op(A,A,null,false,loop,phi);
            phi.setDef(2,back);
            Op use = new Op(null,A,null,false,loop,phi);
            loop.setDef(2,loop);
            LRG lrg = alloc.newLRG(phi);
            lrg._mask = A;
            alloc.union(lrg,init); alloc.union(lrg,back);
            lrg.selfConflict(back);
            if( phiConflict ) lrg.selfConflict(phi);
            assertTrue(alloc.splitSelfConflict((byte)0,lrg));
            assertSame(phi,use.in(1)); // First try leaves the hot definition alone.
            assertSame(back,phi.in(2));
            if( phiConflict ) assertTrue(phi.in(1) instanceof SplitNode);
            // Even if the first attempt made no graph change, retry must split.
            assertTrue(alloc.splitSelfConflict((byte)1,lrg));
            assertTrue(phi.in(2) instanceof SplitNode);
            if( phiConflict ) assertTrue(use.in(1) instanceof SplitNode);
        }
    }

    public static void coalescing() {
        // Merge, incompatible masks, interference, capacity, and adjacency remapping.
        for( int kind=0; kind<5; kind++ ) {
            CodeGen code = graph();
            RegAlloc alloc = new RegAlloc(code);
            Op def = new Op(new RegMask(3L),null,null,false,code._start);
            SplitNode copy = code._mach.split("test",(byte)0,null);
            copy.setDef(0,code._start); copy.setDef(1,def);
            Op use = new Op(null,new RegMask(3L),null,false,code._start,copy);
            LRG from = alloc.newLRG(def), to = alloc.newLRG(copy);
            from._mask = kind==1 ? A : new RegMask(3L);
            to._mask = kind==1 ? B : new RegMask(3L);
            from.machDef(def,false); from.machUse(copy,(short)1,false);
            to.machDef(copy,false); to.machUse(use,(short)1,false);
            LRG left = new LRG((short)100), right = new LRG((short)101);
            if( kind==2 ) { from.addNeighbor(to); to.addNeighbor(from); }
            if( kind>=3 ) {
                from.addNeighbor(left); left.addNeighbor(from);
                if( kind==3 ) { to.addNeighbor(right); right.addNeighbor(to); }
                else { to.addNeighbor(left); left.addNeighbor(to); }
            }
            int fcnt=from.nadj(), tcnt=to.nadj();
            assertTrue(Coalesce.coalesce(0,alloc));
            if( kind==0 || kind==4 ) {
                assertSame(def,use.in(1));
                assertSame(alloc.lrg(def),alloc.lrg(copy));
                assertSame(use,alloc.lrg(def)._machUse);
                assertEquals(1,alloc.lrg(def)._uidx);
                if( kind==4 ) {
                    assertEquals(1,left.nadj());
                    assertSame(alloc.lrg(def),left._adj.at(0));
                    assertSame(left,alloc.lrg(def)._adj.at(0));
                }
            } else {
                assertSame(copy,use.in(1));
                assertNotSame(alloc.lrg(def),alloc.lrg(copy));
                assertEquals(fcnt,from.nadj());
                assertEquals(tcnt,to.nadj());
            }
        }
    }

}
