package com.seaofnodes.simple;

import com.seaofnodes.simple.type.*;
import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.assertSame;

public class TypeTest {

    // Test basic properties and GLB
    @Test
    public void testTypeAdHoc() {
        Assert.assertEquals( Type.BOTTOM, TypeInteger.TRUE.meet(TypeNil.NIL) );
        Assert.assertEquals( Type.BOTTOM, TypeInteger.TOP .meet(TypeNil.NIL) );

        TypeStruct s1 = TypeStruct.make("s1",false,
                Field.make("a", TypeInteger.BOT,-1, false, false),
                Field.make("b", TypeInteger.BOT,-2, false, false) );
        TypeStruct s2 = TypeStruct.make("s2",false,
                Field.make("a", TypeInteger.BOT,-3, false, false),
                Field.make("b", TypeInteger.BOT,-4, false, false) );
        TypeStruct x1ro = (TypeStruct)s1.makeRO();
        TypeMemPtr p1 = TypeMemPtr.make(s1);
        Assert.assertEquals(x1ro, ((TypeMemPtr)p1.glb(false))._obj);
        Assert.assertNotEquals(s1, s1.dual());
        TypeStruct s1dglb = ((TypeMemPtr)p1.dual().glb(false))._obj;
        Assert.assertTrue(x1ro.isa(s1dglb));

        TypeMem m1 = TypeMem.make(2,TypeNil.NIL);
        TypeMem m2 = TypeMem.make(3,TypeInteger.U16);
        TypeMem m3 = TypeMem.make(4,TypeFloat.F64);
        TypeMem m4 = TypeMem.make(5,TypeInteger.BOT);

        Assert.assertNotEquals(m1, m2);
        Assert.assertNotEquals(m2, m3);
        Assert.assertNotEquals(m3, m4);

        Assert.assertEquals(TypeStruct.BOT, s1.meet(s2));
        Assert.assertEquals(TypeMem   .BOT, m1.meet(m2));
        Assert.assertEquals(TypeMem.make(1,Type.BOTTOM), m1.meet(m3));
        Assert.assertEquals(TypeMem   .BOT, m3.meet(m4));

        Assert.assertEquals(TypeMem.make(2,Type.BOTTOM), m1.glb(false));
        Assert.assertEquals(TypeMem.make(2,Type.XNIL), m1.dual());
        Assert.assertEquals(m4.dual(), m4.glb(false).dual());

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
        Type p1glb = ptr1.glb(false);
        Assert.assertNotEquals(ptr1, p1glb );
        Type p1nro = ptr1nil.makeRO();
        Assert.assertEquals(p1nro, p1glb);

        Assert.assertEquals(ptr1, ptr1.dual().dual());
        Assert.assertTrue(p1glb.makeRO().isa( ptr1.dual().glb(false)));
        Assert.assertEquals(TypeMemPtr.makeNullable(TypeStruct.BOT), ptr1.meet(ptr2nil));
        Assert.assertEquals(p1glb, ptr1.meet(TypeNil.NIL).makeRO());

        TypeMemPtr TOP = TypeMemPtr.TOP;
        TypeMemPtr BOT = TypeMemPtr.makeNullable(TypeStruct.BOT);
        TypeMemPtr PTR = TypeMemPtr.make(TypeStruct.BOT);
        Type NULL = TypeNil.NIL;
        Type PTR_meet_NULL = NULL.meet(PTR);
        Assert.assertEquals(BOT, PTR_meet_NULL);
        Type ptr1_meet_ptr2 = ptr1.meet(ptr2);
        Assert.assertEquals(PTR, ptr1_meet_ptr2);
        Type NULL_join_ptr1 = NULL.join(ptr1);
        Assert.assertEquals(TypePtr.XNPTR, NULL_join_ptr1);
        Type NULL_join_PTR = PTR.join(NULL);
        Assert.assertEquals(TypePtr.XNPTR, NULL_join_PTR);
        Type ptr1_dual = ptr1.dual();
        Type nullableptr1_dual = ptr1nil.dual();

        // Cyclic check
        TypeStruct S1 = ((TypeMemPtr)TypeStruct.SFLT2.field("s1")._t)._obj;
        Assert.assertFalse(s1.isFinal());
        Type s1ro  = S1.makeRO();
        Type s1ro2 = S1.makeRO();
        Assert.assertSame(s1ro,s1ro2);
        Assert.assertTrue(s1ro.isFinal());

        Assert.assertFalse(S1.isConstant());
        Type s1glb  = S1.field("s2")._t.glb(false);
        Type s1glb2 = S1.field("s2")._t.glb(false);
        Assert.assertSame(s1glb,s1glb2);
    }

    // Test theoretical properties.
    // This is a symmetric complete bounded (ranked) lattice.
    // Also, the meet is commutative and associative.
    // The lattice has a dual (symmetric), and join is ~(~x meet ~y).
    // See https://en.wikipedia.org/wiki/Lattice_(order).
    @Test
    public void testLatticeTheory() {
        Type[] ts = Type.gather();

        // Confirm commutative & complete
        for( Type t0 : ts )
            for( Type t1 : ts ) {
                check_commute  (t0,t1);
                check_symmetric(t0,t1);
            }

        // Confirm associative
        for( Type t0 : ts )
            for( Type t1 : ts )
                for( Type t2 : ts )
                    assoc(t0,t1,t2);

        // Confirm symmetry.  If A isa B, then A.join(C) isa B.join(C)
        for( Type t0 : ts )
            for( Type t1 : ts )
                if( t0.isa(t1) )
                    for( Type t2 : ts ) {
                        Type t02 = t0.join(t2);
                        Type t12 = t1.join(t2);
                        Type mt  = t02.meet(t12);
                        assertSame(mt,t12);
                    }
    }

    // By design in meet, args are already flipped to order _type, which forces
    // symmetry for things with badly ordered _type fields.  The question is
    // still interesting for other orders.
    private static void check_commute( Type t0, Type t1 ) {
        if( t0==t1 ) return;
        if( t0.is_simple() && !t1.is_simple() ) return; // By design, flipped the only allowed order
        Type mta = t0.meet(t1);
        Type mtb = t1.meet(t0); // Reverse args and try again
        assertSame(mta,mtb);
    }

    // A & B = MT
    // Expect: ~A & ~MT == ~A
    // Expect: ~B & ~MT == ~B
    private static void check_symmetric( Type t0, Type t1 ) {
        if( t1==t0 ) return;
        Type mt = t0.meet(t1);
        Type dm = mt.dual();
        Type d0 = t0.dual();
        Type d1 = t1.dual();
        Type ta = dm.meet(d1);
        Type tb = dm.meet(d0);
        assertSame(ta,d1);
        assertSame(tb,d0);
    }

    private static void assoc( Type t0, Type t1, Type t2 ) {
        Type t01   = t0 .meet(t1 );
        Type t12   = t1 .meet(t2 );
        Type t01_2 = t01.meet(t2 );
        Type t0_12 = t0 .meet(t12);
        assertSame(t01_2,t0_12);
    }

    // Test cyclic types and meets
    @Test
    public void testCyclic0() {
        Type d0 = TypeStruct.SFLT2.dual();
        Type d1 = d0.dual();
        assertSame(TypeStruct.SFLT2,d1);
    }

    @Test
    public void testGLB() {
        Type[] ts = Type.gather();
        for( Type t0 : ts )
            if( !(t0 instanceof Field || t0 instanceof TypeStruct || t0 instanceof TypeTuple || t0 instanceof TypeConAry ) )
                Assert.assertTrue(t0.isa(t0.glb(false)));
    }

    @Test
    public void testList() {
        TypeStruct list = TypeStruct.open("List");
        TypeMemPtr plist = TypeMemPtr.makeNullable(list);
        list = list.add(Field.make("next", plist, 2, false, false ) );
        list = list.add(Field.make("x", TypeInteger.BOT, 3, false, false));
        // Make a cyclic type
        list = list.close();
        // Fields are mutable
        Assert.assertFalse(list.isFinal());
        TypeStruct flist = (TypeStruct)list.makeRO();
        Assert.assertTrue(flist.isFinal());
        Assert.assertNotSame( list, flist );

    }
}
