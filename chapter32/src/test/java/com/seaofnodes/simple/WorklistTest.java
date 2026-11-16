package com.seaofnodes.simple;

import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorklistTest {
    @Test public void testGuardMergeAfterPredicateRewrite() { guardMergeAfterPredicateRewrite(false); }
    @Test public void testGuardMergeAfterPredicateSubsume() { guardMergeAfterPredicateRewrite(true); }

    private static void guardMergeAfterPredicateRewrite(boolean subsume) {
        CodeGen code = new CodeGen("");
        Node ctrl = new CProjNode(code._start,0,"Ctrl").init();
        Node old = code.con(TypeInteger.BOOL), value = code.con(TypeInteger.BOT);
        IfNode iff = new IfNode(ctrl,old).init();
        Node yes = new CProjNode(iff,0,"True").init();
        Node no = new CProjNode(iff,1,"False").init();
        RegionNode region = new RegionNode(null,null,yes,no).init();
        Node guard = new GuardNode(true,yes,value).init();
        PhiNode phi = new PhiNode("guard",region,guard,code.con(TypeInteger.ZERO)).init();
        phi.keep();
        code._iter._work.clear();
        assertNull(phi.idealize());
        assertFalse(code._iter._work.on(phi));
        // Neither edit changes a Phi input; its predicate dependency must wake it.
        if( subsume ) old.subsume(value);
        else iff.setDef(1,value);
        assertTrue("Predicate rewrite must requeue the guard merge",code._iter._work.on(phi));
        assertSame(value,phi.idealize());
        phi.unkeep();
    }
    @Test public void testKnownIfDoesNotSearchDominators() {
        CodeGen code = new CodeGen("");
        Node ctrl = new CProjNode(code._start,0,"Ctrl").init();
        IfNode outer = new IfNode(ctrl,code.con(TypeInteger.BOOL)).init();
        Node yes = new CProjNode(outer,0,"True").init();
        // Nonconstant, but already known to be nonzero.
        IfNode inner = new IfNode(yes,code.con(TypeInteger.make(1,2))).init();
        assertSame(TypeTuple.IF_TRUE,inner._type);
        assertNull(inner.idealize());
        for( int i=0; i<outer.nDeps(); i++ ) assertNotSame(inner,outer.dep(i));
        CProjNode live = new CProjNode(inner,0,"Live").init();
        CProjNode dead = new CProjNode(inner,1,"Dead").init();
        assertSame(yes,live.idealize());
        assertSame(code.XCTRL,dead.idealize());
    }
}
