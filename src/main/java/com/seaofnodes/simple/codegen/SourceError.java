package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.util.SB;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Format one parser byte offset. Source is read and text built only on error. */
public abstract class SourceError {
    public static String format(CompUnit cu, int pos, String message) {
        String file=cu.sourceFile();
        String name=file==null ? cu._fname+".smp" : file;
        byte[] source;
        try {
            source=cu._src!=null ? cu._src.getBytes(StandardCharsets.UTF_8)
                : file==null ? null : Files.readAllBytes(Path.of(file));
        } catch( IOException ex ) { source=null; }
        return format(name,source,pos,message);
    }

    public static String format(String name, byte[] source, int pos, String message) {
        if( source==null || pos<0 || pos>source.length )
            return name+": error: "+message+" (source unavailable at byte offset "+pos+")";

        int line=1, start=0;
        for( int i=0; i<pos; i++ )
            if( source[i]=='\n' ) { line++; start=i+1; }
        int end=pos;
        while( end<source.length && source[end]!='\n' ) end++;
        if( end>start && source[end-1]=='\r' ) end--;
        int point=Math.min(pos,end);
        String before=new String(source,start,point-start,StandardCharsets.UTF_8);
        String text=new String(source,start,end-start,StandardCharsets.UTF_8);
        SB excerpt=new SB();
        int width=0, caret=0;
        for( int i=0; i<text.length(); ) {
            if( i==before.length() ) caret=width;
            int ch=text.codePointAt(i);
            if( ch=='\t' ) {
                int spaces=8-(width%8);
                for( int j=0; j<spaces; j++ ) excerpt.p(' ');
                width+=spaces;
            } else { excerpt.p(new String(Character.toChars(ch))); width++; }
            i+=Character.charCount(ch);
        }
        if( before.length()==text.length() ) caret=width;
        // Emacs compilation mode defaults to display columns, with 8-column tabs.
        SB sb=new SB().p(name).p(':').p(line).p(':').p(caret+1).p(": error: ").p(message)
            .p("\n    ").p(excerpt.toString());
        sb.p("\n    ");
        for( int i=0; i<caret; i++ ) sb.p('_');
        return sb.p('^').toString();
    }
}
