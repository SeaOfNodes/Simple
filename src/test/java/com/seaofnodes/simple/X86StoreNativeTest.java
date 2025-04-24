package com.seaofnodes.simple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class X86StoreNativeTest {
    @Test public void testConstantStoresAndAdds() throws IOException {
        Path dir = Path.of("build/objs");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("x86ConstantStore.smp"), """
            struct Narrow { u32 x; u32 guard; };
            struct Wide { int x; int guard; };
            val storeNarrow = { !Narrow s -> s.x=4294967295; return 0; };
            val storePositive = { !Wide s -> s.x=81985529216486895; return 0; };
            val storeNegative = { !Wide s -> s.x=-2147483649; return 0; };
            val addPositive = { !Wide s -> s.x+=4294967295; return 0; };
            val addNegative = { !Wide s -> s.x+=-2147483649; return 0; };
            """);
        // Read from C so load forwarding cannot hide an incorrectly encoded Store.
        Files.writeString(dir.resolve("x86ConstantStore.c"), """
            #include <stdint.h>
            struct Narrow { uint32_t x, guard; };
            struct Wide { int64_t x, guard; };
            extern long long storeNarrow(struct Narrow *);
            extern long long storePositive(struct Wide *);
            extern long long storeNegative(struct Wide *);
            extern long long addPositive(struct Wide *);
            extern long long addNegative(struct Wide *);
            int main(void) {
                struct Narrow n = {0, 0x12345678};
                struct Wide w = {0, 0x123456789abcdefLL};
                storeNarrow(&n);
                if (n.x != UINT32_MAX || n.guard != 0x12345678) return 1;
                storePositive(&w);
                if (w.x != 81985529216486895LL || w.guard != 0x123456789abcdefLL) return 2;
                storeNegative(&w);
                if (w.x != -2147483649LL || w.guard != 0x123456789abcdefLL) return 3;
                w.x = 7;
                addPositive(&w);
                if (w.x != 4294967302LL || w.guard != 0x123456789abcdefLL) return 4;
                addNegative(&w);
                if (w.x != 2147483653LL || w.guard != 0x123456789abcdefLL) return 5;
                return 0;
            }
            """);
        TestC.run("build/objs","x86ConstantStore","",-1);
    }
}
