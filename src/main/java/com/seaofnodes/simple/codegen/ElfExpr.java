package com.seaofnodes.simple.codegen;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ElfExpr {
    public static void main(String[] args) {
        try (FileOutputStream fos = new FileOutputStream("hello.o")) {
            ByteBuffer buffer = ByteBuffer.allocate(512);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // === ELF HEADER (64 bytes) ===
            buffer.put(new byte[]{
                    0x7F, 'E', 'L', 'F',  // ELF Magic
                    2, 1, 1, 0,          // 64-bit, little-endian, version
                    0, 0, 0, 0, 0, 0, 0,  // ABI padding
                    2, 0,                // Type: Executable (ET_EXEC)
                    0x3E, 0x00,          // Machine: x86-64
                    1, 0, 0, 0,          // ELF version
                    0x00, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // Entry point
                    0, 0, 0, 0, 0, 0, 0, 0,  // Program header offset
                    64, 0, 0, 0, 0, 0, 0, 0,  // Section header offset
                    0, 0, 0, 0,            // Flags
                    64, 0,                 // ELF header size
                    0, 0,                   // Program header entry size
                    0, 0,                   // Number of program header entries
                    40, 0,                  // Section header entry size
                    2, 0,                   // Number of section headers
                    1, 0                    // Section header string table index
            });

            // === .TEXT SECTION (Executable Code) ===
            byte[] textSection = {
                    // sys_write (write(1, message, 13))
                    (byte) 0x48, (byte) 0xC7, (byte) 0xC0, 0x01, 0x00, 0x00, 0x00,  // mov rax, 1
                    (byte) 0x48, (byte) 0xC7, (byte) 0xC7, 0x01, 0x00, 0x00, 0x00,  // mov rdi, 1
                    (byte) 0x48, (byte) 0xBE, (byte)0x90, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // mov rsi, message
                    (byte) 0x48, (byte) 0xC7, (byte) 0xC2, 0x0D, 0x00, 0x00, 0x00,  // mov rdx, 13
                    (byte) 0x0F, (byte) 0x05,  // syscall

                    // sys_exit (exit(0))
                    (byte) 0x48, (byte) 0xC7, (byte) 0xC0, 0x3C, 0x00, 0x00, 0x00,  // mov rax, 60
                    (byte) 0x48, (byte) 0x31, (byte) 0xFF,  // xor rdi, rdi
                    (byte) 0x0F, (byte) 0x05   // syscall
            };

            // Append section data
            buffer.position(256);
            buffer.put(textSection);

            // === STRING MESSAGE (.data Section) ===
            byte[] message = "Hello, World!\n".getBytes();
            buffer.position(400);
            buffer.put(message);

            // === SECTION HEADERS ===
            buffer.position(64);

            // Null Section (required as first entry)
            buffer.put(new byte[40]);

            // .text Section
            buffer.putInt(1);    // Offset in section string table (".text")
            buffer.putInt(1);    // Type: SHT_PROGBITS
            buffer.putLong(6);   // Flags: Executable | Allocatable
            buffer.putLong(0);   // Virtual address
            buffer.putLong(256); // Offset to section data
            buffer.putLong(textSection.length); // Section size
            buffer.putInt(0);    // Link
            buffer.putInt(0);    // Info
            buffer.putLong(4);   // Address alignment
            buffer.putLong(0);   // No extra entries

            // === SECTION HEADER STRING TABLE ===
            buffer.position(500);
            buffer.put(".text".getBytes());
            buffer.put((byte) 0x00);  // Null terminator

            fos.write(buffer.array());
            System.out.println("ELF object file 'hello.o' created.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
