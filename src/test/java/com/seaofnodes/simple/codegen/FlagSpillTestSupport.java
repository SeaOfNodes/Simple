package com.seaofnodes.simple.codegen;

import com.seaofnodes.isa.X86;
import com.seaofnodes.isa.eval.EvalArm64;
import com.seaofnodes.simple.TestC;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.node.cpus.arm.arm;
import com.seaofnodes.simple.node.cpus.x86_64_v2.x86_64_v2;
import com.seaofnodes.simple.type.TypeInteger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        @Override public void encoding(Encoding enc) { throw new AssertionError(); }
    }

    public static void run() throws IOException {
        StringBuilder asm = new StringBuilder(".text\n");
        for( boolean isArm : new boolean[]{false,true} )
            for( boolean stack : new boolean[]{false,true} ) {
                CodeGen code = new CodeGen("");
                code._callingConv="SystemV";
                code._mach=isArm ? new arm(code) : new x86_64_v2(code);
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

                // Keep the allocated registers, supplying a frame for encoding stack offsets.
                FunNode frame = new FunNode(null,null,"flags",null,(Node)null);
                code._regAlloc=new RegAlloc(code);
                for( Node n : block._outputs )
                    if( alloc.regnum(n)>=0 ) code._regAlloc.newLRG(n,frame)._reg=alloc.regnum(n);
                Encoding save = new Encoding(code), restore = new Encoding(code);
                Encoding enc = save;
                for( Node n : block._outputs ) {
                    if( n==kill ) enc=restore;
                    if( n instanceof SplitNode split ) split.encoding(enc);
                }
                if( isArm ) {
                    for( int bits=0; bits<16; bits++ ) {
                        EvalArm64 cpu = new EvalArm64(new byte[1024],512);
                        System.arraycopy(save._bits.buf(),0,cpu._buf,0,save._bits.size());
                        System.arraycopy(restore._bits.buf(),0,cpu._buf,save._bits.size(),restore._bits.size());
                        cpu.N=(bits&8)!=0; cpu.Z=(bits&4)!=0; cpu.C=(bits&2)!=0; cpu.V=(bits&1)!=0;
                        assertEquals(0,cpu.step(save._bits.size()/4));
                        cpu.N=cpu.Z=cpu.C=cpu.V=false;
                        if( stack ) for( int r=0; r<31; r++ ) cpu.regs[r]=0;
                        assertEquals(0,cpu.step(restore._bits.size()/4));
                        assertEquals((bits&8)!=0,cpu.N); assertEquals((bits&4)!=0,cpu.Z);
                        assertEquals((bits&2)!=0,cpu.C); assertEquals((bits&1)!=0,cpu.V);
                        assertEquals(512,cpu.regs[31]);
                    }
                } else {
                    Encoding body = new Encoding(code);
                    int[] preserved={3,5,6,7,12,13,14,15};
                    for( int r : preserved ) pushPop(body,r,false);
                    X86.imm(body,0x81,5,4,16); // Reserve spill storage.
                    X86.flags(body,TestC.OS.startsWith("Windows") ? 1 : 7,true);
                    bytes(body,save);
                    if( stack ) {
                        for( int r=0; r<16; r++ ) if( r!=4 ) X86.constant(body,r,0);
                    } else X86.reg(body,0x39,0,0); // CMP rax,rax clobbers only flags.
                    bytes(body,restore);
                    X86.flags(body,0,false); // Return the restored flags.
                    X86.imm(body,0x81,0,4,16);
                    for( int i=preserved.length-1; i>=0; i-- ) pushPop(body,preserved[i],true);
                    X86.ret(body);
                    String name=stack ? "flags_stack" : "flags_reg";
                    asm.append(".globl ").append(name).append('\n').append(name).append(":\n.byte ");
                    for( int i=0; i<body._bits.size(); i++ ) {
                        if( i!=0 ) asm.append(',');
                        asm.append(body._bits.buf()[i]&255);
                    }
                    asm.append('\n');
                }
            }
        Path dir=Files.createTempDirectory(Path.of("build/objs"),"flag_spills_");
        Files.writeString(dir.resolve("flags.s"),asm);
        Files.writeString(dir.resolve("flags.c"),"""
            typedef unsigned long long u64;
            u64 __attribute__((CALL_CONV)) flags_reg(u64);
            u64 __attribute__((CALL_CONV)) flags_stack(u64);
            int main(void) {
                const int positions[]={0,2,4,6,7,11};
                for(int bits=0; bits<64; bits++) {
                    u64 flags=0x202;
                    for(int i=0;i<6;i++) if(bits&(1<<i)) flags|=1ULL<<positions[i];
                    if((flags_reg(flags)&0x8d5)!=(flags&0x8d5)) return 1;
                    if((flags_stack(flags)&0x8d5)!=(flags&0x8d5)) return 2;
                }
                return 0;
            }
            """);
        String exe=dir.resolve(TestC.OS.startsWith("Windows") ? "flags.exe" : "flags").toString();
        TestC.exec("gcc","-DCALL_CONV="+(TestC.OS.startsWith("Windows") ? "ms_abi" : "sysv_abi"),
            "-o",exe,dir.resolve("flags.c").toString(),dir.resolve("flags.s").toString());
        TestC.exec(exe);
    }

    private static void bytes(Encoding dst,Encoding src) {
        for( int i=0; i<src._bits.size(); i++ ) dst.add1(src._bits.buf()[i]&255);
    }
    private static void pushPop(Encoding enc,int reg,boolean pop) {
        if( reg>=8 ) enc.add1(0x41);
        enc.add1((pop ? 0x58 : 0x50)+(reg&7));
    }
}
