package com.seaofnodes.simple;

import org.junit.Test;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.codegen.IterPeeps;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import static org.junit.Assert.*;

public class SchedulingTest {
    @Test public void verifierAllowsLoadAfterMemoryMerge() {
        CodeGen code = loadAfterMemoryMerge();
        assertTrue(IterPeeps.schedulableUses(code));
    }

    @Test public void verifierRejectsLoadUsedBeforeMemoryMerge() {
        CodeGen code = loadAfterMemoryMerge();
        ReturnNode ret = (ReturnNode)code._stop.walk(n -> n instanceof ReturnNode ? n : null);
        LoadNode load = (LoadNode)ret.expr();
        // The load may move below its check, but cannot serve a use above its memory.
        ret.setDef(0,load.in(0));
        try {
            IterPeeps.schedulableUses(code);
            fail("A load cannot execute before its memory is available");
        } catch( AssertionError error ) {
            assertTrue(error.getMessage(),error.getMessage().contains("Unschedulable data node LoadNode"));
        }
    }

    private static CodeGen loadAfterMemoryMerge() {
        CodeGen code = new CodeGen("return arg;").parse();
        ReturnNode ret = (ReturnNode)code._stop.walk(n -> n instanceof ReturnNode ? n : null);
        Node ctrl = ret.ctrl();
        IfNode iff = new IfNode(ctrl,ret.expr()).init();
        Node yes = new CProjNode(iff,0,"True").init();
        Node no = new CProjNode(iff,1,"False").init();
        RegionNode region = new RegionNode(null,null,yes,no).init();
        Node phi = new BulkMemPhiNode("mem",region,ret.mem(),ret.mem()).init();
        Node mem = new MemMergeNode(null,phi).init();
        Node load = new LoadNode(null,"[]",2,ctrl,mem,code.con(TypeMemPtr.BOT),code.con(TypeInteger.ZERO)).init();
        ret.setDef(0,region);
        ret.setDef(2,load);
        return code;
    }
    @Test public void nativeFoldedLoadsWithLaterOperands() throws Exception {
        TestC.runSYS("""
            val compare_noInline={ i64[] a, int flag ->
                i64 x=a[0];
                i64 y=flag ? a[1] : a[2];
                return x==y;
            };
            val add_noInline={ i64[] a, int flag ->
                i64 x=a[0];
                i64 y=flag ? a[1] : a[2];
                return x+y;
            };
            val main={ ->
                val a=new i64[3]; a[0]=17; a[1]=17; a[2]=23;
                if(!compare_noInline(a,1) || compare_noInline(a,0)) return 1;
                if(add_noInline(a,1)!=34 || add_noInline(a,0)!=40) return 2;
                return 0;
            };
            ""","scheduleLaterOperands","",-1);
    }

    @Test public void nativeLoadsAcrossWrites() throws Exception {
        TestC.runSYS("""
            val change_noInline={ i64[] a -> a[0]=a[1]; return a[0]; };
            val compare_noInline={ i64[] a ->
                i64 x=a[0];
                return x==change_noInline(a);
            };
            val add_noInline={ i64[] a ->
                i64 x=a[0];
                return x+change_noInline(a);
            };
            val main={ ->
                val a=new i64[2]; a[0]=17; a[1]=23;
                if(compare_noInline(a)) return 1;
                a[0]=17; a[1]=29;
                if(add_noInline(a)!=46) return 2;
                return 0;
            };
            ""","scheduleAcrossWrites","",-1);
    }
}
