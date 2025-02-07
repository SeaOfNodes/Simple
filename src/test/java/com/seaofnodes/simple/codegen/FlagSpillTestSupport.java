package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.arm.arm;
import com.seaofnodes.simple.node.cpus.x86_64_v2.x86_64_v2;
import com.seaofnodes.simple.type.TypeInteger;
import static org.junit.Assert.*;

// Force overlapping flags, then force every GPR unavailable across the gap.
public class FlagSpillTestSupport {
    private static class Op extends MachConcreteNode {
        final RegMask _def, _use, _kill;
        Op(RegMask def,RegMask use,RegMask kill,Node... ins) {
            super(ins); _def=def; _use=use; _kill=kill; _type=TypeInteger.BOT;
        }
        @Override public String op() { return "flagsProbe"; }
        @Override public RegMask outregmap() { return _def; }
        @Override public RegMask regmap(int i) { return _use; }
        @Override public RegMask killmap() { return _kill; }
        @Override public int encoding(java.io.ByteArrayOutputStream enc) { throw new AssertionError(); }
    }

    public static void run() throws Exception {
        for( boolean isArm : new boolean[]{false,true} )
            for( boolean stack : new boolean[]{false,true} ) {
                CodeGen code = new CodeGen("");
                code._callingConv="SystemV";
                code._mach=isArm ? new arm() : new x86_64_v2();
                RegionNode block = new RegionNode(null,code._start,code._start);
                code._cfg.add(block);
                int flagsReg=isArm ? 64 : 32, firstStack=flagsReg+1;
                RegMask flags = new RegMask(flagsReg);
                Op def = new Op(flags,null,null,block);
                RegMask killMask = stack ? (isArm ? new RegMask(0x7fffffffL,1L) : new RegMask(0x1ffffffefL)) : flags;
                Op kill = new Op(null,null,killMask,block);
                new Op(null,flags,null,block,def);
                RegAlloc alloc = new RegAlloc(code);
                alloc.regAlloc();

                int splits=0, slots=0;
                for( Node n : block._outputs ) if( n instanceof SplitNode split ) {
                    int dst=alloc.regnum(n), src=alloc.regnum(n.in(1));
                    assertTrue(split.outregmap().test(dst));
                    assertTrue(split.regmap(1).test(src));
                    for( int fp=isArm ? 32 : 16; fp<flagsReg; fp++ ) {
                        assertFalse("FP output allowed",split.outregmap().test(fp));
                        assertFalse("FP input allowed",split.regmap(1).test(fp));
                    }
                    if( dst>=firstStack || src>=firstStack ) slots++;
                    splits++;
                }
                assertTrue(splits>=2);
                assertEquals(stack,slots!=0);

            }
    }
}
