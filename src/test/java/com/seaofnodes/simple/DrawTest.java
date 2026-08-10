package com.seaofnodes.simple;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import com.seaofnodes.simple.codegen.CodeGen;
import com.seaofnodes.simple.type.TypeInteger;
import com.seaofnodes.simple.util.Ary;
import static org.junit.Assert.*;

// Native raster and game checks; no backend or display is needed.
public class DrawTest {
    @Test public void nativeClockCoordinates() throws Exception {
        String clock=Files.readString(Path.of("docs/examples/Clock.smp"))
            .replace("val main =","val _windowMain =");
        StringBuilder source=new StringBuilder(clock+"""
            val _check_noInline={ f64 turn, int expectedX, int expectedY ->
                int x=360+_sin(turn)*128, y=232-_sin(turn+0.25)*128;
                if( x<expectedX-1 || x>expectedX+1 || y<expectedY-1 || y>expectedY+1 ) {
                    sys.io.p("turn %f(turn): %(x),%(y) expected %(expectedX),%(expectedY)\\n");
                    return 1;
                }
                return 0;
            };
            val main={ ->
            """);
        for(int i=0;i<=60;i++) {
            double turn=i/60.0;
            int x=(int)(360+clockSine(turn)*128), y=(int)(232-clockSine(turn+0.25)*128);
            source.append("if(_check_noInline(").append(turn).append(',').append(x).append(',').append(y)
                .append(")!=0) return 1;");
        }
        source.append("return 0; };");
        TestC.runSYS(source.toString(),"clockCoordinates","",-1);
    }

    private static double clockSine(double turn) {
        turn-=(long)turn;
        boolean negative=turn>0.5;
        double x=(negative ? turn-0.5 : turn)*6.283185307179586;
        double product=x*(3.141592653589793-x);
        return (negative ? -16.0 : 16.0)*product/(49.34802200544679-4*product);
    }

    @Test public void nativeAnalogSweep() throws Exception {
        String clock=Files.readString(Path.of("docs/examples/Clock.smp"))
            .replace("val main =","val _windowMain =");
        StringBuilder source=new StringBuilder(clock+"""
            val _renderCheck_noInline={ int second, int millisecond, int expectedX, int expectedY ->
                val c=new sys.draw.Canvas(720,540);
                _analog(c,3,20,second,millisecond);
                var found=false;
                for( int y=expectedY-2; y<=expectedY+2; y++ )
                    for( int x=expectedX-2; x<=expectedX+2; x++ )
                        if( c.pixels[y*720+x]==0xc3ffcb ) found=true;
                if( !found ) {
                    sys.io.p("missing second hand %(second).%(millisecond) expected %(expectedX),%(expectedY)\\n");
                    return 1;
                }
                // Every dial mark/hand must stay within the dial's bounds.
                for( int y=0; y<540; y++ ) for( int x=0; x<720; x++ )
                    if( (x<204 || x>516 || y<76 || y>388) && c.pixels[y*720+x]!=0 ) {
                        sys.io.p("stray pixel %(x),%(y) color %x(c.pixels[y*720+x]) at %(second).%(millisecond)\\n");
                        return 2;
                    }
                return 0;
            };
            val main={ ->
            """);
        for(int millis:new int[]{0,500,7500,15000,30000,45000,59500}) {
            double turn=millis/60000.0;
            int x=(int)(360+clockSine(turn)*128), y=(int)(232-clockSine(turn+0.25)*128);
            source.append("if(_renderCheck_noInline(").append(millis/1000).append(',').append(millis%1000)
                .append(',').append(x).append(',').append(y).append(")!=0) return 1;");
        }
        source.append("return 0; };");
        TestC.runSYS(source.toString(),"clockAnalogSweep","",-1);
    }

    @Test public void buttonInteractionAndRendering() throws Exception {
        TestC.runSYS("""
            val main={ ->
                val b=new sys.draw.Button(10,10,60,30,"GO");
                if( !b.contains(10,10) || b.contains(70,20) || b.contains(20,40) ) return 1;
                if( b.update(20,20,true) || !b.pressed || b.update(20,20,true) ) return 2;
                if( !b.update(20,20,false) || b.pressed || b.update(20,20,false) ) return 3;
                if( b.update(0,0,true) || b.update(20,20,true) || b.update(20,20,false) ) return 4;
                b.update(20,20,true);
                if( b.update(0,0,false) ) return 5;
                b.update(20,20,true); b.enabled=false;
                if( b.update(20,20,true) || b.pressed || b.update(20,20,false) ) return 6;
                b.enabled=true;
                val c=new sys.draw.Canvas(90,60);
                b.update(20,20,false); b.draw(c,2,9,3,5);
                if( c.pixels[10*90+10]!=9 || c.pixels[13*90+13]!=5 || c.pixels[0]!=0 ) return 7;
                b.update(0,0,false); b.draw(c,2,9,3,5);
                if( c.pixels[13*90+13]!=3 ) return 8;
                return 0;
            };
            ""","drawButton","",-1);
    }

    @Test public void clockFacesWithoutWindow() throws Exception {
        String clock=Files.readString(Path.of("docs/examples/Clock.smp"))
            .replace("val main =","val _windowMain =");
        CodeGen code=new CodeGen(null,null,new Ary<>(new String[]{TestC.RELEASE_SYS_DIR}),"ClockRaster",clock+"""
            val main={ ->
                val c=new sys.draw.Canvas(720,540);
                _digit(c,10,10,8);
                if( c.pixels[10*720+40]!=GREEN || c.pixels[82*720+40]!=GREEN || c.pixels[154*720+40]!=GREEN ) return 1;
                _digit(c,10,10,1);
                if( c.pixels[10*720+40]!=DIM || c.pixels[40*720+78]!=GREEN || c.pixels[40*720+14]!=DIM ) return 2;
                c.clear(BACKGROUND); _digital(c,23,59,58);
                if( c.pixels[214*720+254]!=GREEN || c.pixels[262*720+470]!=GREEN ) return 3;
                c.clear(BACKGROUND); _analog(c,3,0,0,0);
                if( c.pixels[232*720+430]!=GREEN || c.pixels[160*720+360]!=0xc3ffcb || c.pixels[232*720+360]!=GREEN ) return 4;
                if( _sin(0.25)<0.999 || _sin(0.75)>-0.999 || _sin(1.0)!=0.0 ) return 5;
                return 0;
            };
            return main();
            """,126L,true,TypeInteger.BOT).driver(CodeGen.Phase.TypeCheck);
        assertEquals("0",Eval2.eval(code,0,3000000));
    }

    @Test public void rectanglesAndColors() throws Exception {
        TestC.runSYS("""
            val main = { ->
                val c = new sys.draw.Canvas(12,10);
                u32 color = sys.draw.rgb(300,-1,128);
                if( color!=16711808 ) return 1;
                c.clear(7).fillRect(-2,-1,5,4,color);
                for( int y=0; y<10; y++ )
                    for( int x=0; x<12; x++ )
                        if( c.pixels[y*12+x]!=(x<3 && y<3 ? color : 7) ) return 2;
                c.clear(0).rect(2,2,7,6,1,9);
                for( int y=0; y<10; y++ )
                    for( int x=0; x<12; x++ ) {
                        var inside = 2<=x<9 && 2<=y<8;
                        var border = inside && (x==2 || x==8 || y==2 || y==7);
                        if( c.pixels[y*12+x]!=(border ? 9 : 0) ) return 3;
                    }
                c.clear(5).fillRect(20,0,8,8,1).rect(0,0,12,10,0,1);
                c.fillRect(0,0,-1,5,1).pixel(-1,0,1).pixel(12,9,1);
                for( int i=0; i<c.pixels#; i++ ) if( c.pixels[i]!=5 ) return 4;
                return 0;
            };
            ""","drawRectangles","",-1);
    }

    @Test public void circlesLinesAndText() throws Exception {
        TestC.runSYS("""
            val main = { ->
                val c = new sys.draw.Canvas(16,16);
                c.fillCircle(8,8,3,1);
                for( int y=0; y<16; y++ )
                    for( int x=0; x<16; x++ )
                        if( c.pixels[y*16+x]!=((x-8)*(x-8)+(y-8)*(y-8)<=9 ? 1 : 0) ) {
                            sys.io.p("circle mismatch %(x) %(y): %(c.pixels[y*16+x])\\n");
                            return 1;
                        }
                c.clear(0).circle(8,8,3,1,2);
                if( c.pixels[8*16+8]!=0 || c.pixels[8*16+11]!=2 || c.pixels[8*16+10]!=0 ) return 2;
                c.clear(0).circle(8,8,3,3,2);
                if( c.pixels[8*16+8]!=2 ) return 8;
                c.clear(0).line(-5,8,20,8,3,3);
                for( int y=0; y<16; y++ )
                    for( int x=0; x<16; x++ )
                        if( c.pixels[y*16+x]!=(7<=y<=9 ? 3 : 0) ) return 3;
                c.clear(0).line(8,8,8,8,2,4);
                if( c.pixels[8*16+8]!=4 || c.pixels[8*16+9]!=4 || c.pixels[9*16+9]!=0 ) return 4;
                c.clear(0).line(2,2,13,13,1,5);
                if( c.pixels[7*16+7]!=5 || c.pixels[7*16+8]!=0 ) return 5;
                c.clear(0).text(0,0,"x",1,6).text(8,0,"X",1,6);
                for( int y=0; y<5; y++ )
                    for( int x=0; x<3; x++ )
                        if( c.pixels[y*16+x]!=c.pixels[y*16+x+8] ) return 6;
                c.clear(0).text(0,0,":/",2,6);
                for( int y=0; y<10; y++ )
                    for( int x=0; x<16; x++ ) {
                        int row=y/2, col=x/2;
                        var colon=col==1 && (row==1 || row==3);
                        var slash=(row<2 && col==6) || (row==2 && col==5) || (row>2 && col==4);
                        if( c.pixels[y*16+x]!=((colon || slash) ? 6 : 0) ) return 9;
                    }
                c.clear(0).fillCircle(-1,-1,2,7);
                if( c.pixels[0]!=7 || c.pixels[2]!=0 ) return 7;
                return 0;
            };
            ""","drawCurves","",-1);
    }

    @Test public void graphicalGameWithoutWindow() throws Exception {
        String game=Files.readString(Path.of("docs/examples/TicTacToeGraphical.smp"))
            .replace("val main =","val _windowMain =");
        TestC.runSYS(game+"""

            val main = { ->
                val board = new u8[9];
                _reset(board);
                if( _square(48,168)!=0 || _square(551,671)!=8 || _square(552,400)!=-1 || _square(100,167)!=-1 ) return 1;
                if( !_move(board,4) || board[4]!='X' || board[0]!='O' ) return 2;
                if( _sound(board)!=sys.audio.BOOP ) return 10;
                if( _move(board,4) || _move(board,0) || _move(board,-1) || _move(board,9) ) return 3;
                val moves = "1879";
                _reset(board);
                for( int i=0; i<moves#; i++ )
                    if( !_move(board,moves[i]-'1') ) return 4;
                if( _winner(board)!='X' || _move(board,1) ) return 5;
                if( _sound(board)!=sys.audio.WIN ) return 11;
                val screen = new sys.draw.Canvas(600,780);
                _draw(screen,board,-1);
                if( screen.pixels[252*600+132]==screen.pixels[0] ) return 6;
                _reset(board);
                val draw = "12768";
                for( int i=0; i<draw#; i++ ) _move(board,draw[i]-'1');
                if( !_finished(board) || _winner(board)!=0 ) return 7;
                _reset(board);
                _move(board,0); _move(board,1); _move(board,3);
                if( _winner(board)!='O' ) return 8;
                if( _sound(board)!=sys.audio.LOSE ) return 12;
                _draw(screen,board,-1);
                _reset(board);
                if( _finished(board) ) return 9;
                _draw(screen,board,4);
                return 0;
            };
            ""","drawGame","",-1);
    }
}
