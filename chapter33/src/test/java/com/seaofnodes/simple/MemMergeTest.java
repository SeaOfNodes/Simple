package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeMem;
import org.junit.Test;
import static org.junit.Assert.*;

public class MemMergeTest {
    @Test public void testDenseAliasesAndDefaults() {
        CodeGen code = new CodeGen("");
        Node defaults = code.con(TypeMem.START), value = code.con(TypeMem.BOT).keep();
        MemMergeNode mem = new MemMergeNode(null,defaults);
        mem.alias(10000,value);
        mem.alias(2,value);
        mem.alias(513,value);
        assertEquals(5,mem.nIns());
        assertEquals(2,mem.aliasAt(2));
        assertEquals(513,mem.aliasAt(3));
        assertEquals(10000,mem.aliasAt(4));
        assertSame(value,mem.alias(10000));
        assertSame(defaults,mem.alias(512));
        assertNull(mem.explicitAlias(9999));
        assertSame(value,mem.explicitAlias(10000));
        mem.alias(513,null);
        assertEquals(4,mem.nIns());
        assertEquals(10000,mem.aliasAt(3));
        assertSame(value,mem.alias(10000));
        mem.alias(2,defaults);
        assertSame(mem,mem.idealize());
        assertEquals(3,mem.nIns());
        assertSame(defaults,mem.alias(2));
        // Removing/reinserting colliding keys must not leave tombstones or stale slots.
        for( int i=0; i<100; i++ ) {
            mem.alias(100+i*8,value);
            mem.alias(100+i*8,null);
        }
        assertEquals(3,mem.nIns());
        mem.alias(10000,defaults);
        assertSame(mem,mem.idealize());
        assertEquals(2,mem.nIns());
        // Retain the sparse implementation's fold eligibility for this experiment.
        assertNull(mem.idealize());
        assertSame(defaults,new MemMergeNode(null,defaults).idealize());
        value.unkeep();
    }

    @Test public void testIndependentCopyMaps() {
        CodeGen code = new CodeGen("");
        Node defaults = code.con(TypeMem.START), value = code.con(TypeMem.BOT);
        MemMergeNode mem = new MemMergeNode(null,defaults);
        mem.alias(200,value);
        MemMergeNode copy = (MemMergeNode)mem.copyEmpty();
        for( Node in : mem._inputs ) copy.addDef(in);
        copy.alias(7,value);
        assertEquals(3,mem.nIns());
        assertEquals(4,copy.nIns());
        assertSame(defaults,mem.alias(7));
        assertSame(value,mem.alias(200));
        copy.alias(200,null);
        assertSame(value,mem.alias(200));
        assertSame(defaults,copy.alias(200));
        code._phase = CodeGen.Phase.Select;
        MemMergeNode selected = new MemMergeNode(mem);
        assertEquals(200,selected.aliasAt(2));
        assertSame(value,selected.alias(200));
    }

    @Test public void testStackedMergesKeepOverrides() {
        CodeGen code = new CodeGen("");
        Node defaults = code.con(TypeMem.START), value = code.con(TypeMem.BOT), override = code.con(TypeMem.TOP);
        MemMergeNode inner = new MemMergeNode(null,defaults);
        inner.alias(8,value);
        inner.alias(10000,value);
        MemMergeNode outer = new MemMergeNode(null,inner);
        outer.alias(10000,override);
        assertSame(outer,outer.idealize());
        assertEquals(4,outer.nIns());
        assertSame(defaults,outer.in(1));
        assertSame(value,outer.alias(8));
        assertSame(override,outer.alias(10000));
    }
}
