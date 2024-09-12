package com.seaofnodes.simple;

import com.seaofnodes.simple.type.*;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class TypeTest {

    // Test basic properties and GLB
    @Test
    public void testTypeAdHoc() {
        TypeStruct s1 = TypeStruct.make("s1", new Field[]{
                Field.make("a",-1, TypeInteger.BOT),
                Field.make("b",-2, TypeInteger.BOT) });
        TypeStruct s2 = TypeStruct.make("s2", new Field[]{
                Field.make("a",-3, TypeInteger.BOT),
                Field.make("b",-4, TypeInteger.BOT) });
        Assert.assertEquals(s1, s1.glb());
        Assert.assertNotEquals(s1, s1.dual());
        Assert.assertEquals(s1, s1.dual().glb());

        TypeMem m1 = TypeMem.make(1,TypeInteger.ZERO);
        TypeMem m2 = TypeMem.make(2,TypeInteger.U16);
        TypeMem m3 = TypeMem.make(3,TypeFloat.BOT);
        TypeMem m4 = TypeMem.make(4,TypeInteger.BOT);

        Assert.assertNotEquals(m1, m2);
        Assert.assertNotEquals(m2, m3);
        Assert.assertNotEquals(m3, m4);

        Assert.assertEquals(TypeStruct.BOT, s1.meet(s2));
        Assert.assertEquals(TypeMem.make(-1,TypeInteger.U16), m1.meet(m2));
        Assert.assertEquals(TypeMem   .BOT, m2.meet(m3));
        Assert.assertEquals(TypeMem   .BOT, m3.meet(m4));

        Assert.assertEquals(TypeMem.make(1,TypeInteger.BOT), m1.glb());
        Assert.assertEquals(TypeMem.make(1,TypeInteger.ZERO), m1.dual());
        Assert.assertEquals(m4.dual(), m4.glb().dual());

        TypeMemPtr ptr1 = TypeMemPtr.make(s1);
        Assert.assertEquals(s1, ptr1._obj);
        TypeMemPtr ptr2 = TypeMemPtr.make(s2);
        Assert.assertEquals(s2, ptr2._obj);

        TypeMemPtr ptr1nil = TypeMemPtr.make(s1, true);
        Assert.assertEquals(s1, ptr1nil._obj);
        Assert.assertTrue(ptr1nil._nil);
        Assert.assertFalse(ptr1nil._obj==null);
        TypeMemPtr ptr2nil = TypeMemPtr.make(s2, true);
        Assert.assertEquals(s2, ptr2nil._obj);

        Assert.assertNotEquals(ptr1, ptr2);
        Assert.assertNotEquals(ptr1, ptr1.glb());
        Assert.assertEquals(ptr1nil, ptr1.glb());

        Assert.assertEquals(ptr1, ptr1.dual().dual());
        Assert.assertEquals(ptr1.glb(), ptr1.dual().glb());
        Assert.assertEquals(TypeMemPtr.make(TypeStruct.BOT,true), ptr1.meet(ptr2nil));
        Assert.assertEquals(ptr1.glb(), ptr1.meet(TypeMemPtr.NULLPTR));

        TypeMemPtr TOP = TypeMemPtr.TOP;
        TypeMemPtr BOT = TypeMemPtr.make(TypeStruct.BOT,true );
        TypeMemPtr PTR = TypeMemPtr.make(TypeStruct.BOT,false);
        TypeMemPtr NULL = TypeMemPtr.NULLPTR;
        Type PTR_meet_NULL = NULL.meet(PTR);
        Assert.assertEquals(BOT, PTR_meet_NULL);
        Type ptr1_meet_ptr2 = ptr1.meet(ptr2);
        Assert.assertEquals(PTR, ptr1_meet_ptr2);
        Type NULL_join_ptr1 = NULL.join(ptr1);
        Assert.assertEquals(TOP, NULL_join_ptr1);
        Type NULL_join_PTR = PTR.join(NULL);
        Assert.assertEquals(TOP, NULL_join_PTR);
        Type ptr1_dual = ptr1.dual();
        Type nullableptr1_dual = ptr1nil.dual();
    }

    // Test theoretical properties.
    // This is a symmetric complete bounded (ranked) lattice.
    // Also the meet is commutative and associative.
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
