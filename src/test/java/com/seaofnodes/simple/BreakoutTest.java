package com.seaofnodes.simple;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

// Exercise the actual Simple game and rasterizer in native code, without a window.
public class BreakoutTest {
    private static String game() throws Exception {
        String source = Files.readString(Path.of("docs/examples/Breakout.smp"));
        // Physics/rendering tests supply their own entry point. Input and
        // sound are exercised by GraphicsTest with the complete program.
        int end = source.indexOf("// Short, fading triangle waves");
        assertTrue("Breakout physics/rendering section",end >= 0);
        String physics = source.substring(0,end);
        // The shared square-root helper now follows the input code.
        int sqrt = source.indexOf("val _sqrt =");
        if( sqrt >= end ) {
            int close = source.indexOf("\n};",sqrt);
            assertTrue("Square-root helper end",close >= 0);
            physics += source.substring(sqrt,close+4);
        }
        return physics;
    }

    @Test public void paddleLivesAndPause() throws Exception {
        TestC.runSYS(game()+"""
            val main={ ->
                val g=new Game;
                if( g.remaining!=192 || g.score!=0 || g.lives!=3 || g.phase!=READY ) return 1;
                _tick(g,1);
                if( g.paddle<510.39 || g.paddle>510.41 || g.x!=g.paddle ) return 2;
                g._paddle(-100.0); if( g.paddle != 80.0 ) return 3;
                g._paddle(2000.0); if( g.paddle != 928.0 ) return 4;
                _reset(g);
                g.x=g.paddle-50.0; _rebound(g);
                if( g.dx > -0.78 || g.dy>=0.0 ) return 5;
                g.x=g.paddle+50.0; _rebound(g);
                if( g.dx < 0.78 || g.dy>=0.0 ) return 6;
                g.x=g.paddle; _rebound(g);
                if( g.dy > -0.99 || g.dx*g.dx+g.dy*g.dy<0.999999 ) return 7;
                _serve(g); g.paused=true;
                f64 x=g.x, y=g.y, paddle=g.paddle;
                _tick(g,1);
                if( g.x!=x || g.y!=y || g.paddle!=paddle ) return 8;
                g.paused=false;
                for( int i=0; i<3; i++ ) {
                    _serve(g); g.x=40.0; g.y=723.0; g.dx=0.0; g.dy=1.0;
                    _tick(g,0);
                    if( g.lives!=2-i || g.backWall || g.sound!=5 ) return 9;
                }
                if( g.phase!=LOST ) return 10;
                _serve(g); if( g.phase!=LOST ) return 11;
                _reset(g);
                if( g.phase!=READY || g.lives!=3 || g.boosted || g.paused || g.speed!=320.0 ) return 12;
                return 0;
            };
            ""","breakoutControls","",-1);
    }

    @Test public void collisionsAndBoost() throws Exception {
        TestC.runSYS(game()+"""
            val main={ ->
                val g=new Game;
                _serve(g);
                g.x=29.1; g.y=400.0; g.dx=-1.0; g.dy=0.0;
                _tick(g,0);
                if( g.dx<=0.0 || g.x<29.0 || g.sound!=1 ) return 1;
                g.x=978.9; g.dx=1.0; _tick(g,0);
                if( g.dx>=0.0 || g.x>979.0 ) return 2;
                // Front of the bottom row: one point, no acceleration or boost.
                g.x=44.0; g.y=330.0; g.dx=0.0; g.dy=-1.0;
                _tick(g,0);
                if( g.bricks[168]!=0 || g.score!=1 || g.dy<=0.0 || g.boosted || g.soundDepth!=1 ) return 3;
                // Top of a brick alone does not arm the back-wall bonus.
                g.x=44.0; g.y=146.0; g.dy=1.0;
                _tick(g,0);
                if( g.bricks[0]!=0 || g.score!=9 || g.speed!=565.0 || g.boosted || g.soundDepth!=8 ) return 4;
                // Back wall arms it; hitting a side still must not trigger it.
                g.x=84.0; g.y=89.1; g.dy=-1.0;
                _tick(g,0);
                if( !g.backWall || g.dy<=0.0 || g.boosted ) return 5;
                g.x=60.0; g.y=160.0; g.dx=1.0; g.dy=0.0;
                _tick(g,0);
                if( g.bricks[1]!=0 || g.dx>=0.0 || g.boosted ) return 6;
                g.x=124.0; g.y=146.0; g.dx=0.0; g.dy=1.0;
                _tick(g,0);
                if( !g.boosted || g.speed<932.24 || g.speed>932.26 || g.dy>=0.0 || g.sound!=4 ) return 7;
                // Another back hit cannot multiply the speed a second time.
                g.x=164.0; g.y=146.0; g.dy=1.0;
                _tick(g,0);
                if( g.speed>932.26 || g.bricks[3]!=0 ) return 8;
                // A boosted front hit cannot tunnel through the brick either.
                g.x=84.0; g.y=331.0; g.dy=-1.0;
                _tick(g,0);
                if( g.bricks[169]!=0 || g.dy<=0.0 || g.speed<932.24 ) return 9;
                // Actual paddle contact disarms the trip and chooses its angle.
                g.x=g.paddle+50.0; g.y=668.0; g.dy=1.0; g.dx=0.0;
                _tick(g,0);
                if( g.dy>=0.0 || g.dx<0.78 || g.backWall || !g.boosted ) return 10;
                // A lost ball clears both kinds of acceleration, preserving the wall/score.
                int score=g.score, remaining=g.remaining;
                g.x=40.0; g.y=723.0; g.dx=0.0; g.dy=1.0;
                _tick(g,0);
                if( g.phase!=READY || g.lives!=2 || g.speed!=320.0 || g.depth!=1 || g.boosted ) return 11;
                if( g.score!=score || g.remaining!=remaining ) return 12;
                _serve(g);
                if( g.speed!=320.0 ) return 13;
                // The new ball can earn its own boost.
                g.sound=0; g.backWall=true;
                g.x=204.0; g.y=146.0; g.dx=0.0; g.dy=1.0;
                _tick(g,0);
                if( !g.boosted || g.speed<932.24 || g.sound!=4 ) return 14;
                return 0;
            };
            ""","breakoutCollisions","",-1);
    }

    @Test public void clearEveryBrickAndRender() throws Exception {
        TestC.runSYS(game()+"""
            val main={ ->
                val g=new Game;
                val c=new sys.draw.Canvas(WIDTH,HEIGHT);
                _draw(c,g);
                for( int row=0; row<ROWS; row++ )
                    if( c.pixels[(BRICK_TOP+row*BRICK_DY+4)*WIDTH+28]!=COLORS[row] ) return 1;
                if( c.pixels[675*WIDTH+504]!=CYAN || c.pixels[663*WIDTH+504]!=WHITE ) return 2;
                // Save a native-rendered preview for manual visual review.
                int fd=sys.io.create("build/objs/breakout-preview.bgra");
                i64 pixels=c.pixels;
                if( fd<0 || sys.libc.write(fd,pixels,c.pixels#*4)!=c.pixels#*4 ) return 3;
                sys.io.close(fd);
                _serve(g);
                // Drive through every brick's front, bottom row first.
                for( int i=COLS*ROWS-1; i>=0; i-- ) {
                    g.x=LEFT+20.0+(i-i/COLS*COLS)*BRICK_DX;
                    g.y=BRICK_TOP+1.0+(i/COLS)*BRICK_DY+BRICK_H+RADIUS;
                    g.dx=0.0; g.dy=-1.0;
                    _tick(g,0);
                    if( g.bricks[i]!=0 ) return 4;
                }
                if( g.phase!=WON || g.remaining!=0 || g.score!=864 || g.sound!=6 ) return 5;
                f64 y=g.y; _tick(g,0);
                if( g.y!=y ) return 6;
                _reset(g);
                for( int i=0; i<g.bricks#; i++ ) if( g.bricks[i]!=1 ) return 7;
                if( g.score!=0 || g.remaining!=192 ) return 8;
                return 0;
            };
            ""","breakoutClear","",-1);
        byte[] pixels=Files.readAllBytes(Path.of("build/objs/breakout-preview.bgra"));
        assertEquals(1008*760*4,pixels.length);
        var image=new BufferedImage(1008,760,BufferedImage.TYPE_INT_RGB);
        var bytes=ByteBuffer.wrap(pixels).order(ByteOrder.LITTLE_ENDIAN);
        for(int y=0;y<760;y++) for(int x=0;x<1008;x++) image.setRGB(x,y,bytes.getInt());
        ImageIO.write(image,"png",Path.of("build/objs/breakout-preview.png").toFile());
    }
}
