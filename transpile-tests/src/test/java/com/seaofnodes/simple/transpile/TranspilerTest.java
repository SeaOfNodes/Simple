package com.seaofnodes.simple.transpile;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TranspilerTest {
    @Test
    public void testParser() throws IOException {
        var cwd = Path.of(".").toAbsolutePath().normalize();
        assertEquals("transpile-tests", cwd.getFileName().toString());
        var all = JUnitParser.parseRepository(cwd.getParent());
        assertTrue(all.size() >= 14);

        var chapter09 = all.get("chapter09");
        assertEquals(9, chapter09.size());

        var class6 = chapter09.get(5);
        assertEquals("Chapter06Test", class6.name());
        assertEquals(1, class6.methods().stream().filter(m -> m.name.equals("testPeepholeReturn")).count());
    }
}
