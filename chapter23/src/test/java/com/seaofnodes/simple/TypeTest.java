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
        Assert.assertEquals( Type.BOTTOM, TypeInteger.TOP.meet(TypeNil.NIL) );


        TypeStruct s1 = TypeStruct.make("s1", new Field[]{
                Field.make("a", TypeInteger.BOT,-1, false, false),
                Field.make("b", TypeInteger.BOT,-2, false, false) });
        TypeStruct s2 = TypeStruct.make("s2", new Field[]{
                Field.make("a", TypeInteger.BOT,-3, false, false),
                Field.make("b", TypeInteger.BOT,-4, false, false) });
        Assert.assertEquals(s1.makeRO(), s1.glb(false));
        Assert.assertNotEquals(s1, s1.dual());
        Assert.assertEquals(s1.makeRO(), s1.dual().glb(false));

        TypeMem m1 = TypeMem.make(1,TypeNil.NIL);
        TypeMem m2 = TypeMem.make(2,TypeInteger.U16);
        TypeMem m3 = TypeMem.make(3,TypeFloat.F64);
        TypeMem m4 = TypeMem.make(4,TypeInteger.BOT);

        Assert.assertNotEquals(m1, m2);
        Assert.assertNotEquals(m2, m3);
        Assert.assertNotEquals(m3, m4);

        Assert.assertEquals(TypeStruct.BOT, s1.meet(s2));
        Assert.assertEquals(TypeMem   .BOT, m1.meet(m2));
        Assert.assertEquals(TypeMem.make(0,Type.BOTTOM), m1.meet(m3));
        Assert.assertEquals(TypeMem   .BOT, m3.meet(m4));

        Assert.assertEquals(TypeMem.make(1,Type.BOTTOM), m1.glb(false));
        Assert.assertEquals(TypeMem.make(1,Type.XNIL), m1.dual());
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
        Assert.assertNotEquals(ptr1, ptr1.glb(false));
        Assert.assertEquals(ptr1nil.makeRO(), ptr1.glb(false));

        Assert.assertEquals(ptr1, ptr1.dual().dual());
        Assert.assertEquals(ptr1.glb(false).makeRO(), ptr1.dual().glb(false));
        Assert.assertEquals(TypeMemPtr.makeNullable(TypeStruct.BOT), ptr1.meet(ptr2nil));
        Assert.assertEquals(ptr1.glb(false), ptr1.meet(TypeNil.NIL).makeRO());

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
        Type d0 = TypeStruct.S1.dual();
        Type d1 = d0.dual();
        assertSame(TypeStruct.S1,d1);
    }

}
