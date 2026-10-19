package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.type.TypeMem;
import org.junit.Test;
import static org.junit.Assert.*;

public class BulkMemPhiTest {
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
