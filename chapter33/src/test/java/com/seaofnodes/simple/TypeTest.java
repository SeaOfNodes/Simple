package com.seaofnodes.simple;

import com.seaofnodes.simple.type.*;
import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.assertSame;

public class TypeTest {

    @Test public void testFinalPointerCycles() {
        TypeStruct cycle = TypeStruct.open("FinalPointerCycle");
        Assert.assertFalse(cycle.isFinal());
        TypeMemPtr self = TypeMemPtr.make(cycle).withAccess(true);
        cycle = cycle.add(Field.make("self",self,2,true))
                     .add(Field.make("value",TypeInteger.BOT,3,true)).close();
        self = (TypeMemPtr)cycle.field("self")._t;
        assertSame(cycle,self._obj);
        Assert.assertTrue(self.isFinal());
        Assert.assertTrue(cycle.isFinal());
        assertSame(cycle,cycle.makeRO());

        // Slot finality and pointer access are independent: a final slot can
        // still hold a writable pointer, and a readonly pointer can refer to
        // an object whose declared fields are mutable.
        Assert.assertFalse(TypeMemPtr.make(cycle).isFinal());
        Assert.assertFalse(Field.make("slot",self,4,false).isFinal());
        TypeStruct mutable = cycle.addOrUpdate(Field.make("value",TypeInteger.BOT,3,false));
        Assert.assertFalse(mutable.isFinal());
        Assert.assertTrue(TypeMemPtr.make(mutable).withAccess(true).isFinal());
        Assert.assertTrue(cycle.isFinal());
    }

    @Test public void testConstantPointerCycles() {
        TypeStruct cycle = TypeStruct.open("ConstantPointerCycle");
        TypeMemPtr self = TypeMemPtr.make((byte)2,cycle,true);
        cycle = cycle.add(Field.make("self",self,2,true))
                     .add(Field.make("value",TypeInteger.constant(7),3,true)).close();
        self = (TypeMemPtr)cycle.field("self")._t;
        assertSame(cycle,self._obj);
        Assert.assertTrue(self.isConstant());
        Assert.assertTrue(cycle.isConstant());
        // A constant shape does not make an arbitrary pointer a singleton.
        Assert.assertFalse(TypeMemPtr.make(cycle).isConstant());

        TypeStruct varying = TypeStruct.make("VaryingConstantReferent",false,
            Field.make("value",TypeInteger.BOT,4,false));
        Assert.assertFalse(varying.isConstant());
        // Conversely, a singleton pointer can refer to mutable contents.
        Assert.assertTrue(TypeMemPtr.make((byte)2,varying,true).isConstant());

        TypeMem memory = TypeMem.make(2,cycle,true,true,true,XInt.EMPTY,XInt.EMPTY);
        Assert.assertTrue(memory.isConstant());
        Assert.assertFalse(TypeMem.make(2,cycle,true,false,true,XInt.EMPTY,XInt.EMPTY).isConstant());
        // Repeated aggregate types are sharing, not recursion. Neither query
        // should require or leave behind a cyclic-traversal visit marker.
        TypeTuple tuple = TypeTuple.make(cycle,memory,cycle);
        Assert.assertTrue(tuple.isConstant());
        Assert.assertTrue(tuple.isConstant());
        Assert.assertFalse(TypeTuple.make(cycle,TypeInteger.BOT).isConstant());
    }

    @Test public void testConstantArrayElements() {
        TypeConAry range=TypeConAry.make(TypeInteger.U16,new long[]{5,6,7});
        Assert.assertEquals("[5-7]",range.toString());
        Assert.assertEquals("[5-7]",TypeConAry.make(TypeInteger.U8,new long[]{5,6,7}).toString());
        Assert.assertEquals("[\"abc\"]",TypeConAry.make("abc").toString());
        for( TypeInteger storage : new TypeInteger[]{TypeInteger.BOOL,TypeInteger.I8,TypeInteger.U8,
                TypeInteger.I16,TypeInteger.U16,TypeInteger.I32,TypeInteger.U32,TypeInteger.BOT} ) {
            TypeConAry empty=TypeConAry.make(storage,new long[0]);
            assertSame(storage.dual(),empty.elem());
            assertSame(storage,((TypeConAry)empty.dual()).elem());
            for( long[] values : new long[][]{{0},{1},{0,1},{storage._min,storage._max}} ) {
                TypeConAry array=TypeConAry.make(storage,values);
                Assert.assertTrue(empty.isa(array));
                assertSame(array,empty.meet(array));
                assertSame(empty,array.join(empty));
                check_symmetric(empty,array);
            }
        }
    }

    @Test public void testOpaqueExternalEscapes() {
        TypeMem defaults = TypeMem.START.makeFrom(XInt.make(2),XInt.make(3));
        TypeMem prior = TypeMem.START.makeFrom(XInt.make(5),XInt.make(7));
        TypeMemPtr opaque = TypeMemPtr.makeNullable(TypeStruct.forward("OpaqueExternal"));
        TypeMem escaped = prior.escapesFrom(opaque,defaults);
        assertSame(XInt.meet(prior._escFs,defaults._escFs),escaped._escFs);
        assertSame(XInt.meet(prior._escAs,defaults._escAs),escaped._escAs);
        // Before type closure the same name might still resolve locally.
        assertSame(XInt.FULL,prior.escapesFrom(opaque,TypeMem.BOT)._escFs);
        for( Type unknown : new Type[]{Type.BOTTOM,TypeScalar.BOT,TypePtr.PTR,
                TypeMemPtr.make(TypeStruct.open("LocalStillOpen"))} ) {
            TypeMem broad = prior.escapesFrom(unknown,defaults);
            assertSame(XInt.FULL,broad._escFs);
            assertSame(XInt.FULL,broad._escAs);
        }
        // Concrete private callbacks must not be restricted to public names.
        TypeFunPtr callback = TypeFunPtr.make1((byte)2,true,TypeFunPtr.TEMPTY,TypeInteger.BOT,11);
        assertSame(XInt.make(11),TypeMem.START.escapesFrom(callback,defaults)._escFs);
    }

    // Aggregate finality is carried by its fields. Canonicalizing the unused
    // aggregate bit must commute with dual, even for named (non-high) structs.
    @Test public void testAggregateMemoryDual() {
        TypeStruct obj = TypeStruct.make("AggregateDual",false,
            Field.make("x",TypeInteger.BOT,2,false),
            Field.make("y",TypeInteger.ZERO,3,true));
        for( Type t : new Type[]{Type.BOTTOM,Type.TOP,TypeStruct.BOT,TypeStruct.TOP,obj,obj.dual()} )
            for( boolean one : new boolean[]{false,true} ) {
                TypeMem mem = TypeMem.make(1,t,one,false,false,XInt.EMPTY,XInt.EMPTY);
                assertSame(mem,TypeMem.make(1,t,one,false,true,XInt.EMPTY,XInt.EMPTY));
                assertSame(mem,mem.dual().dual());
                assertSame(mem.dual(),TypeMem.make(1,t.dual(),!one,true,true,XInt.FULL,XInt.FULL));
            }
        // Precise field slices still distinguish final and mutable storage.
        Assert.assertNotEquals(TypeMem.make(2,TypeInteger.BOT,false,false,false,XInt.EMPTY,XInt.EMPTY),
                               TypeMem.make(2,TypeInteger.BOT,false,false,true ,XInt.EMPTY,XInt.EMPTY));
        for( boolean one : new boolean[]{false,true} )
            for( boolean fin : new boolean[]{false,true} ) {
                TypeMem bulk = TypeMem.make(1,Type.TOP,one,false,false,XInt.EMPTY,XInt.EMPTY);
                TypeMem slice = TypeMem.make(2,TypeInteger.BOT,false,false,fin,XInt.EMPTY,XInt.EMPTY);
                assertSame(slice,bulk.meet(slice));
                check_symmetric(bulk,slice);
                check_symmetric(bulk.dual(),slice.dual());
            }
    }

    // Refining an unknown pointer must remove possible escapes, not add them.
    // Inlining can expose the abstract self pointer before its concrete class.
    @Test public void testAbstractPointerEscapes() {
        TypeMemPtr object = TypeMemPtr.make(TypeStruct.make("EscapingCallback",false,
            Field.make("callback",TypeFunPtr.TEST,2,false)));
        for( Type precise : new Type[]{object,TypeFunPtr.TEST} )
            for( Type broad : new Type[]{Type.BOTTOM,TypeScalar.BOT,TypePtr.PTR,TypePtr.NPTR} ) {
                Assert.assertTrue(precise.isa(broad));
                TypeMem unknown = TypeMem.START.escapes(broad);
                assertSame(XInt.FULL,unknown._escFs);
                assertSame(XInt.FULL,unknown._escAs);
                Assert.assertTrue(TypeMem.START.escapes(precise).isa(unknown));
            }
        for( Type empty : new Type[]{Type.TOP,TypeScalar.TOP,TypePtr.XPTR,TypePtr.XNPTR,TypeNil.NIL} )
            assertSame(TypeMem.START,TypeMem.START.escapes(empty));
    }

    @Test public void testBoundedBits() {
        int[] sparse = XInt.make(XInt.make(2),65);
        for( int[] bits : new int[][]{XInt.EMPTY,XInt.FULL,sparse,XInt.dual(sparse)} )
            for( int limit : new int[]{0,1,32,65,66,100} ) {
                int next = XInt.next(bits,-1,limit);
                for( int bit=0; bit<limit; bit++ )
                    if( XInt.bit(bits,bit) ) {
                        Assert.assertEquals(bit,next);
                        next = XInt.next(bits,next,limit);
                    }
                Assert.assertEquals(-1,next);
            }
    }

    // Removing zero must not reset the widening history of a remaining range.
    @Test public void testNonZeroWidening() {
        for( byte widen=0; widen<=3; widen++ ) {
            TypeInteger pos = TypeInteger.make(0,255,widen).nonZero();
            TypeInteger neg = TypeInteger.make(-255,0,widen).nonZero();
            Assert.assertEquals("positive range widening",widen,pos._widen);
            Assert.assertEquals("negative range widening",widen,neg._widen);
            assertSame(TypeInteger.make(1,255,widen),pos);
            assertSame(TypeInteger.make(-255,-1,widen),neg);
            // Singleton ranges retain their widening level too.
            assertSame(TypeInteger.make(1,1,widen),TypeInteger.make(0,1,widen).nonZero());
            assertSame(TypeInteger.make(-1,-1,widen),TypeInteger.make(-1,0,widen).nonZero());
        }
        Assert.assertNull(TypeInteger.ZERO.nonZero());
    }

    // Struct hashes omit field types, so interning compares unlike leaf kinds.
    @Test
    public void testCyclicLeafKinds() {
        TypeStruct bottom = TypeStruct.make("B14LeafKinds",false,
                Field.make("x",Type.BOTTOM,2,false));
        TypeStruct one = TypeStruct.make("B14LeafKinds",false,
                Field.make("x",TypeFloat.constant(1.0),2,false));
        TypeStruct two = TypeStruct.make("B14LeafKinds",false,
                Field.make("x",TypeFloat.constant(2.0),2,false));

        // Meeting creates a new struct while cyclic equality is active. Its
        // float leaf must compare unequal to the previously interned BOTTOM.
        TypeStruct meet = (TypeStruct)one.meet(two);
        assertSame(TypeFloat.F32,meet._fields[0]._t);
        assertSame(meet,two.meet(one));
        assertSame(meet,TypeStruct.make("B14LeafKinds",false,
                Field.make("x",TypeFloat.F32,2,false)));
        assertSame(bottom,meet.meet(bottom));
        assertSame(meet.dual(),one.dual().join(two.dual()));
    }

    // Test basic type properties
    @Test
    public void testTypeAdHoc() {
        Assert.assertEquals( TypeScalar.BOT, TypeInteger.TRUE.meet(TypeNil.NIL) );
        Assert.assertEquals( TypeScalar.BOT, TypeInteger.TOP .meet(TypeNil.NIL) );

        TypeStruct s1 = TypeStruct.make("s1",false,
                Field.make("a", TypeInteger.BOT,-1, false),
                Field.make("b", TypeInteger.BOT,-2, false) );
        TypeStruct s2 = TypeStruct.make("s2",false,
                Field.make("a", TypeInteger.BOT,-3, false),
                Field.make("b", TypeInteger.BOT,-4, false) );
        TypeMemPtr p1 = TypeMemPtr.make(s1);
        Assert.assertNotEquals(s1, s1.dual());

        TypeMem m1 = TypeMem.make(2,TypeNil.NIL);
        TypeMem m2 = TypeMem.make(3,TypeInteger.U16);
        TypeMem m3 = TypeMem.make(4,TypeFloat.F64);
        TypeMem m4 = TypeMem.make(5,TypeInteger.BOT);

        Assert.assertNotEquals(m1, m2);
        Assert.assertNotEquals(m2, m3);
        Assert.assertNotEquals(m3, m4);

        Assert.assertEquals(TypeStruct.BOT, s1.meet(s2));
        TypeMem scalarMemBot = TypeMem.make(1,TypeScalar.BOT,false,false,false,XInt.FULL,XInt.FULL);
        Assert.assertEquals(scalarMemBot, ((TypeMem)m1.meet(m2)).makeFrom( XInt.FULL, XInt.FULL));
        Assert.assertEquals(TypeMem.make(1,TypeScalar.BOT), m1.meet(m3));
        Assert.assertEquals(scalarMemBot, ((TypeMem)m3.meet(m4)).makeFrom( XInt.FULL, XInt.FULL));

        Assert.assertEquals(TypeMem.make(2,Type.XNIL,true,true,true, XInt.FULL, XInt.FULL), m1.dual());

        TypeMemPtr ptr1 = TypeMemPtr.make(s1);
        Assert.assertEquals(s1, ptr1._obj);
        TypeMemPtr ptr2 = TypeMemPtr.make(s2);
        Assert.assertEquals(s2, ptr2._obj);

        TypeMemPtr ptr1nil = TypeMemPtr.makeNullable(s1);
        Assert.assertEquals(s1, ptr1nil._obj);
        Assert.assertTrue( ptr1nil.nullable() );
        Assert.assertNotNull( ptr1nil._obj );
        TypeMemPtr ptr2nil = TypeMemPtr.makeNullable( s2);
        Assert.assertEquals(s2, ptr2nil._obj);

        Assert.assertNotEquals(ptr1, ptr2);
        Assert.assertEquals(ptr1, ptr1.dual().dual());
        Assert.assertEquals(TypeMemPtr.makeNullable(TypeStruct.BOT).withAccess(false).withPartial(false), ptr1.meet(ptr2nil));

        TypeMemPtr TOP = TypeMemPtr.TOP;
        TypeMemPtr BOT = TypeMemPtr.makeNullable(TypeStruct.BOT);
        TypeMemPtr PTR = TypeMemPtr.make(TypeStruct.BOT);
        Type NULL = TypeNil.NIL;
        Type PTR_meet_NULL = NULL.meet(PTR);
        Assert.assertEquals(BOT, PTR_meet_NULL);
        Type ptr1_meet_ptr2 = ptr1.meet(ptr2);
        Assert.assertEquals(PTR.withAccess(false).withPartial(false), ptr1_meet_ptr2);
        Type NULL_join_ptr1 = NULL.join(ptr1);
        Assert.assertEquals(TypePtr.XNPTR, NULL_join_ptr1);
        Type NULL_join_PTR = PTR.join(NULL);
        Assert.assertEquals(TypePtr.XNPTR, NULL_join_PTR);
        Type ptr1_dual = ptr1.dual();
        Type nullableptr1_dual = ptr1nil.dual();

        // Cyclic check
        TypeStruct S1 = ((TypeMemPtr)TypeStruct.SFLT1.field("s1")._t)._obj.close();
        Assert.assertFalse(s1.isFinal());
        Type s1ro  = S1.makeRO();
        Type s1ro2 = S1.makeRO();
        Assert.assertSame(s1ro,s1ro2);
        Assert.assertTrue(s1ro.isFinal());

        Assert.assertFalse(S1.isConstant());
    }

    // Test theoretical properties.
    // This is a symmetric complete bounded (ranked) lattice.
    // Also, the meet is commutative and associative.
    // The lattice has a dual (symmetric), and join is ~(~x meet ~y).
    // See https://en.wikipedia.org/wiki/Lattice_(order).
    @Test
    public void testLatticeTheory() {
        Type[] ts = Type.gather();
        // Make partitions the outer loop between independent JVMs. A direct
        // JUnit/IDE invocation retains the full coverage.
        int parts = Integer.parseInt(System.getProperty("simple.lattice.parts","1"));
        int part  = Integer.parseInt(System.getProperty("simple.lattice.part" ,"0"));
        Assert.assertTrue("Invalid lattice partition",parts > 0 && parts <= ts.length && part >= 0 && part < parts);

        // Every triple reuses these pairs. Compute both operand orders rather
        // than assuming commutativity in the table we are about to check.
        Type[][] meets = new Type[ts.length][ts.length];
        Type[][] joins = new Type[ts.length][ts.length];
        for( int i=0; i<ts.length; i++ )
            for( int j=0; j<ts.length; j++ ) {
                meets[i][j] = ts[i].meet(ts[j]);
                joins[i][j] = ts[i].join(ts[j]);
            }

        // Confirm commutative & complete, once per unordered pair.
        for( int i=part; i<ts.length; i+=parts ) {
            assertSame(ts[i],meets[i][i]);
            for( int j=i; j<ts.length; j++ ) {
                assertSame(meets[i][j],meets[j][i]);
                check_symmetric(ts[i],ts[j],meets[i][j]);
            }
        }

        // Each unordered triple represents all six permutations. Check every
        // intermediate result in both operand orders; a pair's meet need not
        // itself be in ts, so its commutativity must not be assumed.
        for( int i=part; i<ts.length; i+=parts )
            for( int j=i; j<ts.length; j++ )
                for( int k=j; k<ts.length; k++ )
                    assoc(ts[i],ts[j],ts[k],meets[i][j],meets[i][k],meets[j][k]);

        // Confirm symmetry.  If A isa B, then A.join(C) isa B.join(C)
        for( int i=part; i<ts.length; i+=parts ) {
            Type t0 = ts[i];
            for( int j=0; j<ts.length; j++ )
                if( meets[i][j]==ts[j] )
                    for( int k=0; k<ts.length; k++ ) {
                        Type t02 = joins[i][k];
                        Type t12 = joins[j][k];
                        Type mt  = t02.meet(t12);
                        if( mt!=t12 ) assertSame(t0+" isa "+ts[j]+" joined "+ts[k], mt,t12);
                    }
        }
    }

    // A & B = MT
    // Expect: ~A & ~MT == ~A
    // Expect: ~B & ~MT == ~B
    private static void check_symmetric( Type t0, Type t1 ) {
        check_symmetric(t0,t1,t0.meet(t1));
    }

    private static void check_symmetric( Type t0, Type t1, Type mt ) {
        if( t1==t0 ) return;
        Type dm = mt.dual();
        Type d0 = t0.dual();
        Type d1 = t1.dual();
        Type ta = dm.meet(d1);
        Type tb = dm.meet(d0);
        if( ta!=d1 ) assertSame(t0+" & "+t1+" mt="+mt, ta,d1);
        if( tb!=d0 ) assertSame(t0+" & "+t1+" mt="+mt, tb,d0);
    }

    private static void assoc( Type a, Type b, Type c, Type ab, Type ac, Type bc ) {
        // The six original associativity equalities connect these six values
        // in a cycle. Equality to one common result checks the same property.
        Type result = ab.meet(c);
        if( result!=c.meet(ab) || result!=ac.meet(b) || result!=b.meet(ac) ||
            result!=bc.meet(a) || result!=a.meet(bc) )
            Assert.fail("Associativity: "+a+" & "+b+" & "+c);
    }

    // Test cyclic types and meets
    @Test
    public void testCyclic0() {
        Type d0 = TypeStruct.SFLT1.dual();
        Type d1 = d0.dual();
        assertSame(TypeStruct.SFLT1,d1);
    }

    @Test
    public void testList() {
        TypeStruct list = TypeStruct.open("List");
        TypeMemPtr plist = TypeMemPtr.makeNullable(list);
        list = list.add(Field.make("next", plist, 2, false) );
        list = list.add(Field.make("x", TypeInteger.BOT, 3, false));
        // Make a cyclic type
        list = list.close();
        // Fields are mutable
        Assert.assertFalse(list.isFinal());
        TypeStruct flist = (TypeStruct)list.makeRO();
        Assert.assertTrue(flist.isFinal());
        Assert.assertNotSame( list, flist );
    }

    @Test
    public void testOpen() {
        TypeStruct open = TypeStruct.make("List",false);
        TypeMemPtr plist = TypeMemPtr.makeNullable(open);
        TypeStruct list = open.add(Field.make("next", plist, 2, false) );
        list = list.add(Field.make("x", TypeInteger.BOT, 3, false));
        // Make a cyclic type
        list = list.close();
        Type mt = open.meet(list);
        Assert.assertEquals(list,mt);
    }

    @Test public void testConstantArrayPointerLattice() {
        TypeMemPtr empty=TypeMemPtr.make((byte)2,TypeStruct.makeAry("[]u8",2,3,TypeConAry.make("")),true);
        TypeMemPtr now=TypeMemPtr.make((byte)2,TypeStruct.makeAry("[]u8",2,3,TypeConAry.make("now")),true);
        TypeMemPtr abc=TypeMemPtr.make((byte)2,TypeStruct.makeAry("[]u8",2,3,TypeConAry.make("abc")),true);
        Assert.assertTrue(empty.isConstant());
        Assert.assertTrue(now.isConstant());
        Assert.assertFalse(empty.meet(now).isConstant());
        Assert.assertFalse(now.meet(abc).isConstant());
        Type[] types={empty,now,abc,empty.dual(),now.dual(),abc.dual(),now.makeVar()};
        for( Type a : types ) for( Type b : types ) {
            check_symmetric(a,b);
            Assert.assertTrue(a.isa(a.meet(b)));
            for( Type c : types ) {
                assertSame(a.meet(b).meet(c),a.meet(b.meet(c)));
                Assert.assertTrue(a.join(c).isa(a.meet(b).join(c)));
            }
        }
    }
}
