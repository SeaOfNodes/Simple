package com.seaofnodes.simple;

import com.seaofnodes.simple.util.Ary;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class TicTacToeTest {
    private static String exe;

    @BeforeClass public static void compileGame() throws Exception {
        exe=TestC.compile(Files.readString(Path.of("docs/examples/TicTacToe.smp")),
            "ticTacToeGames",new Ary<>(new String[]{TestC.RELEASE_SYS_DIR}),
            TestC.CALL_CONVENTION,null,null,-1);
    }

    private static String play(String keys) throws Exception {
        Process child=new ProcessBuilder(exe).redirectErrorStream(true).start();
        try {
            child.getOutputStream().write(keys.getBytes(StandardCharsets.US_ASCII));
            child.getOutputStream().close();
            assertTrue("Game did not finish",child.waitFor(10,TimeUnit.SECONDS));
            String text=new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            assertEquals(text,0,child.exitValue());
            return text;
        } finally {
            child.destroyForcibly();
        }
    }

    private static String board(String squares) {
        StringBuilder out=new StringBuilder();
        for(int row=0;row<3;row++) {
            if(row>0) out.append("---+---+---\n");
            for(int col=0;col<3;col++) {
                if(col>0) out.append('|');
                out.append(' ').append(squares.charAt(row*3+col)).append(' ');
            }
            out.append('\n');
        }
        return out.toString();
    }

    @Test public void testPlayerWin() throws Exception {
        String text=play("1879");
        assertTrue(text,text.endsWith(board("X OOO XXX")+"You win!\n"));
        assertEquals(3,text.split("CPU plays ",-1).length-1);
    }

    @Test public void testCPUWinsEveryLine() throws Exception {
        // Three rows, three columns, and both diagonals in complete games.
        String[][] games={
            {"4697","OOOXOXX X"}, {"1278","XXOOOOXX "}, {"5234","OXXXX OOO"},
            {"2893","OXXOO OXX"}, {"134","XOXXO  O "}, {"5472","OXOXXOX O"},
            {"234","OXXXO   O"}, {"124","XXOXO O  "}
        };
        for(String[] game:games) {
            String text=play(game[0]);
            assertTrue(game[0]+"\n"+text,text.endsWith(board(game[1])+"CPU wins!\n"));
        }
    }

    @Test public void testDraw() throws Exception {
        String text=play("12768");
        assertTrue(text,text.endsWith(board("XXOOOXXXO")+"It's a draw!\n"));
        assertEquals(4,text.split("CPU plays ",-1).length-1);
    }

    @Test public void testBusySquaresAndInvalidInputDoNotTakeATurn() throws Exception {
        // X takes 5 and O takes 1; neither occupied square can be overwritten.
        String text=play("x\r\n551q");
        assertEquals(text,1,text.split("Choose a square",-1).length-1);
        assertEquals(text,2,text.split("That square is occupied",-1).length-1);
        assertEquals(text,1,text.split("CPU plays ",-1).length-1);
        assertTrue(text,text.contains("Your move (1-9): \nChoose a square from 1 to 9: 5\n"
            +board("    X    ")));
        assertTrue(text,text.contains(board("O   X    ")));
        assertTrue(text,text.endsWith("Your move (1-9): "
            +"\nThat square is occupied. Your move (1-9): "
            +"\nThat square is occupied. Your move (1-9): \nGoodbye!\n"));
    }

    @Test public void testCPUBlocksAndEOFQuits() throws Exception {
        String text=play("12");
        assertTrue(text,text.contains("CPU plays 3.\n"+board("XXO O    ")));
        assertTrue(text,text.endsWith("Goodbye!\n"));
    }
}
