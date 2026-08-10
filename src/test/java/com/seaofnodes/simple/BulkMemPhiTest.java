package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeMem;
import com.seaofnodes.simple.type.Type;
import com.seaofnodes.simple.type.XInt;
import org.junit.Test;
import static org.junit.Assert.*;

public class BulkMemPhiTest {
    @Test public void testUnescapedSliceKeepsItsOwnEscapes() {
        CodeGen code = new CodeGen("");
        code._phase = CodeGen.Phase.Lower;
        Node defaults = code.con(TypeMem.START);
        Node method = code.con(TypeMem.make(55,Type.BOTTOM,false,false,false,XInt.make(4),XInt.EMPTY));
        MemMergeNode fields = new MemMergeNode(null,defaults);
        fields.alias(55,method);
        fields.init();
        assertSame("The bulk excludes this unescaped field",TypeMem.START,fields._type);
        Node ctrl = new CProjNode(code._start,0,"Ctrl").init();
        Node iff = new IfNode(ctrl,code.con(TypeInteger.BOOL)).init();
        RegionNode region = new RegionNode(null,null,
            new CProjNode(iff,0,"True").init(),
            new CProjNode(iff,1,"False").init()).init();
        BulkMemPhiNode bulk = new BulkMemPhiNode("$mem",region,fields,defaults).init();
        MemMergeNode split = (MemMergeNode)bulk.idealize();
        Node precise = split.alias(55);
        assertTrue("A new slice must not inherit an escape bound that excludes its method",
                   precise.compute().isa(precise._type));
        precise.peephole();
        assertTrue(XInt.bit(((TypeMem)precise._type)._escFs,4));
    }

    // Reduced from Capitalize's Lower-phase failure. A precise MemMerge slot
    // may still be supplied by bulk memory which covers the requested alias.
    @Test public void testBulkInPreciseSlot() {
        CodeGen code=new CodeGen("");
        code._phase=CodeGen.Phase.Lower;
        Node m0=code.con(TypeMem.START), m1=code.con(TypeMem.BOT);
        Node ctrl=new CProjNode(code._start,0,"Ctrl").init();
        Node iff=new IfNode(ctrl,code.con(TypeInteger.BOOL)).init();
        RegionNode region=new RegionNode(null,null,
            new CProjNode(iff,0,"True").init(),
            new CProjNode(iff,1,"False").init()).init();
        BulkMemPhiNode bulk=new BulkMemPhiNode("$mem",region,m0,m1).init();
        // A later init check proves its call arm dead. Its precise Phi can
        // collapse before the surrounding aggregate gets another peephole.
        RegionNode done=new RegionNode(null,null,region,code.XCTRL).init();
        MemPhiNode precise=new MemPhiNode("$4",4,done,bulk,m1).init();
        MemMergeNode merge=new MemMergeNode(null,m0);
        merge.alias(4,precise);
        merge.init();
        Node folded=precise.peepholeOpt();
        assertSame(bulk,folded);
        precise.subsume(folded);

        assertFalse(bulk.isSplit(4));
        assertSame(bulk,merge.alias(4));
        // Splitting this still-covered alias must tolerate the direct bulk use.
        Node mem = bulk.idealize();
        bulk.subsume(mem);
    }
}
