package com.seaofnodes.simple;

import com.seaofnodes.simple.node.StopNode;
import com.seaofnodes.simple.type.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class TestType {

    // Test basic properties and GLB
    @Test
    public void testTypeAdHoc() {
        TypeStruct s1 = TypeStruct.make("s1", new Field[]{
                Field.make("s1", TypeInteger.BOT, "a"),
                Field.make("s1", TypeInteger.BOT, "b") });
        TypeStruct s2 = TypeStruct.make("s2", new Field[]{
                Field.make("s2", TypeInteger.BOT, "a"),
                Field.make("s2", TypeInteger.BOT, "b") });
        Assert.assertEquals(s1, s1.glb());
        Assert.assertNotEquals(s1, s1.dual());
        Assert.assertEquals(s1, s1.dual().glb());

        TypeMem m1 = TypeMem.make(s1.get("a")._alias);
        TypeMem m2 = TypeMem.make(s1.get("b")._alias);
        TypeMem m3 = TypeMem.make(s2.get("a")._alias);
        TypeMem m4 = TypeMem.make(s2.get("b")._alias);

        Assert.assertNotEquals(m1, m2);
        Assert.assertNotEquals(m2, m3);
        Assert.assertNotEquals(m3, m4);

        Assert.assertEquals(TypeStruct.BOT, s1.meet(s2));
        Assert.assertEquals(TypeMem   .BOT, m1.meet(m2));
        Assert.assertEquals(TypeMem   .BOT, m2.meet(m3));
        Assert.assertEquals(TypeMem   .BOT, m3.meet(m4));

        Assert.assertEquals(TypeMem.BOT, m1.glb());
        Assert.assertEquals(m1, m1.dual());
        Assert.assertEquals(TypeMem.TOP, m1.glb().dual());

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
        Assert.assertEquals(TypeMemPtr.BOT, ptr1.meet(ptr2nil));
        Assert.assertEquals(ptr1.glb(), ptr1.meet(TypeMemPtr.NULL));
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
        Type ta = mt.dual().meet(t1.dual());
        Type tb = mt.dual().meet(t0.dual());
        assertSame(ta,t1.dual());
        assertSame(tb,t0.dual());
    }

    private static void assoc( Type t0, Type t1, Type t2 ) {
        Type t01   = t0 .meet(t1 );
        Type t12   = t1 .meet(t2 );
        Type t01_2 = t01.meet(t2 );
        Type t0_12 = t0 .meet(t12);
        assertSame(t01_2,t0_12);
    }

}
