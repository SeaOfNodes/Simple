package com.seaofnodes.simple;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

// Native raster and game checks; no backend or display is needed.
public class DrawTest {
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
