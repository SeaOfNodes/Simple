package com.seaofnodes.isa;

import java.io.ByteArrayOutputStream;
import java.util.HexFormat;
import java.util.function.Consumer;

/** Fixed instruction bytes, independent of the compiler and register allocator. */
public class X86Test {
    static class Bytes extends ByteArrayOutputStream implements CodeSink {
        @Override public Bytes add1(int value) { write(value); return this; }
    }
    static void check(String expected, Consumer<CodeSink> emit) {
        var bytes=new Bytes();
        emit.accept(bytes);
        String actual=HexFormat.of().formatHex(bytes.toByteArray());
        assert expected.equals(actual) : "Expected "+expected+", got "+actual;
    }
    public static void main(String[] args) {
        check("4d03ca", b -> X86.reg(b,0x03,9,10));
        check("4d0fafca", b -> X86.reg(b,0x0FAF,9,10));
        check("4981c17fffffff", b -> X86.imm(b,0x81,0,9,-129));
        check("4983c180", b -> X86.imm(b,0x81,0,9,-128));
        check("4983c17f", b -> X86.imm(b,0x81,0,9,127));
        check("4981c180000000", b -> X86.imm(b,0x81,0,9,128));
        check("4d6bca80", b -> X86.imul(b,9,10,-128));
        check("4d69ca80000000", b -> X86.imul(b,9,10,128));

        // TEST; SETZ; MOVZX. Both overlapping and distinct input/output registers.
        int[][] regs={{0,0},{1,1},{7,7},{9,9},{1,9},{9,1},{6,0},{15,8}};
        String[] not={"4885c00f94c00fb6c0","4885c90f94c10fb6c9",
            "4885ff400f94c7400fb6ff","4d85c9410f94c1450fb6c9",
            "4d85c90f94c10fb6c9","4885c9410f94c1450fb6c9",
            "4885c0400f94c6400fb6f6","4d85c0410f94c7450fb6ff"};
        for( int i=0; i<regs.length; i++ ) {
            int dst=regs[i][0], src=regs[i][1];
            check(not[i],b -> X86.not(b,dst,src));
        }

        // SIB, rbp/r13 zero displacement, displacement boundaries, and an extended index.
        check("488b0424", b -> X86.load(b,3,false,false,0,4,-1,0,0));
        check("498b0424", b -> X86.load(b,3,false,false,0,12,-1,0,0));
        check("488b4500", b -> X86.load(b,3,false,false,0,5,-1,0,0));
        check("498b4500", b -> X86.load(b,3,false,false,0,13,-1,0,0));
        check("488b437f", b -> X86.load(b,3,false,false,0,3,-1,127,0));
        check("488b8380000000", b -> X86.load(b,3,false,false,0,3,-1,128,0));
        check("488b4380", b -> X86.load(b,3,false,false,0,3,-1,-128,0));
        check("488b837fffffff", b -> X86.load(b,3,false,false,0,3,-1,-129,0));
        check("4b8b44e508", b -> X86.load(b,3,false,false,0,13,12,8,3));
        check("4f8d4ca508", b -> X86.lea(b,9,13,12,8,2));
        check("4e8d0ce520000000", b -> X86.lea(b,9,-1,12,32,3));

        check("480fbe03", b -> X86.load(b,0,true,false,0,3,-1,0,0));
        check("480fb603", b -> X86.load(b,0,false,false,0,3,-1,0,0));
        check("480fbf03", b -> X86.load(b,1,true,false,0,3,-1,0,0));
        check("480fb703", b -> X86.load(b,1,false,false,0,3,-1,0,0));
        check("486303", b -> X86.load(b,2,true,false,0,3,-1,0,0));
        check("8b03", b -> X86.load(b,2,false,false,0,3,-1,0,0));
        check("f3470f104ca508", b -> X86.load(b,2,false,true,9,13,12,8,2));
        check("f2470f104ca508", b -> X86.load(b,3,false,true,9,13,12,8,2));

        check("408823", b -> X86.store(b,0,false,4,3,-1,0,0)); // spl, not ah
        check("408833", b -> X86.store(b,0,false,6,3,-1,0,0)); // sil, not dh
        check("44883b", b -> X86.store(b,0,false,15,3,-1,0,0));
        check("6647894ca508", b -> X86.store(b,1,false,9,13,12,8,2));
        check("47894ca508", b -> X86.store(b,2,false,9,13,12,8,2));
        check("4f894ca508", b -> X86.store(b,3,false,9,13,12,8,2));
        check("f3470f114ca508", b -> X86.store(b,2,true,9,13,12,8,2));
        check("f2470f114ca508", b -> X86.store(b,3,true,9,13,12,8,2));
        check("c603ff", b -> X86.storeImm(b,0,-1,3,-1,0,0));
        check("6643c744a5083412", b -> X86.storeImm(b,1,0x1234,13,12,8,2));
        check("c70378563412", b -> X86.storeImm(b,2,0x12345678,3,-1,0,0));
        check("48c703ffffffff", b -> X86.storeImm(b,3,-1,3,-1,0,0));
        check("803bff", b -> X86.cmpImm(b,0,-1,3,-1,0,0));
        check("6643817ca5083412", b -> X86.cmpImm(b,1,0x1234,13,12,8,2));
        check("813b78563412", b -> X86.cmpImm(b,2,0x12345678,3,-1,0,0));
        check("48813b15cd5b07", b -> X86.cmpImm(b,3,123456789,3,-1,0,0));
        check("49f7da", b -> X86.unary(b,0xF7,3,10));
        check("49c1e1ff", b -> X86.shift(b,4,9,255));
        check("489949f7fa", b -> X86.divide(b,10));
        check("f2450f58ca", b -> X86.sse(b,0xF2,0x0F58,false,9,10));
        check("f24d0f2aca", b -> X86.sse(b,0xF2,0x0F2A,true,9,10));
        check("66450f2eca", b -> X86.sse(b,0x66,0x0F2E,false,9,10));
        check("4d0fbeca", b -> X86.extend(b,8,true,9,10));
        check("4c0fb6ce", b -> X86.extend(b,8,false,9,6));
        check("6647394ca508", b -> X86.cmpMem(b,1,9,13,12,8,2,false));
        check("66473b4ca508", b -> X86.cmpMem(b,1,9,13,12,8,2,true));
        check("403833", b -> X86.cmpMem(b,0,6,3,-1,0,0,false));
        check("400f95c6400fb6f6", b -> X86.set(b,5,6));
        check("410f95c1450fb6c9", b -> X86.set(b,5,9));
        check("4d33c9", b -> X86.constant(b,9,0));
        check("49c7c1ffffffff", b -> X86.constant(b,9,-1));
        check("41b9ffffffff", b -> X86.constant(b,9,0xFFFFFFFFL));
        check("49b98877665544332211", b -> X86.constant(b,9,0x1122334455667788L));
        check("4c8d0d00000000", b -> { assert X86.rip(b,0,0x8D,true,9)==7; });
        check("f2440f100d00000000", b -> { assert X86.rip(b,0xF2,0x0F10,false,9)==9; });
        check("e800000000c3", b -> { X86.call(b); X86.ret(b); });
        check("ffd0", b -> X86.callRegister(b,0));
        check("41ffd2", b -> X86.callRegister(b,10));
        check("41519d", b -> X86.flags(b,9,true));
        check("9c4159", b -> X86.flags(b,9,false));
        check("ff7424088f442410", b -> X86.stackCopy(b,8,16));
        check("4d8bca", b -> X86.move(b,false,false,9,10));
        check("4d0f28ca", b -> X86.move(b,true,true,9,10));
        check("664d0f6eca", b -> X86.move(b,true,false,9,10));
        check("664d0f7ed1", b -> X86.move(b,false,true,9,10));
        branch("eb7f",129,-1);
        branch("eb80",-126,-1);
        branch("e97d000000",130,-1);
        branch("e97cffffff",-127,-1);
        branch("757f",129,5);
        branch("0f857c000000",130,5);
        branch("0f857bffffff",-127,5);
        System.out.println("Shared x86 encoding tests passed");
    }
    static void branch(String expected, int delta, int condition) {
        int size=X86.branchSize(delta,condition>=0);
        byte[] bytes=new byte[size];
        var shortForm=new Bytes();
        X86.branch(shortForm,condition);
        System.arraycopy(shortForm.toByteArray(),0,bytes,0,2);
        X86.patchBranch(bytes,0,size,delta,condition);
        assert expected.equals(HexFormat.of().formatHex(bytes));
    }
}
