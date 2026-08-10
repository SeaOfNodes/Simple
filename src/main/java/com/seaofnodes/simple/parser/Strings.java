package com.seaofnodes.simple.parser;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;

/** Interpolation expands to ordinary sys.text calls. The library itself uses
 * no interpolation, so it can be compiled from source without staging. */
final class Strings {
    private final Parser P;
    Strings(Parser p) { P=p; }

    Node parse() {
        Lexer start=P.loc();
        P.require("\"");
        Lexer.StringPart part=P._lexer.stringPart(start);
        if( part.end() ) return P.allocations.newString(part.text());
        Node text=P.references.classReference("sys.text",P.loc()).keep();
        P.classInit(P.loc(),text);
        Node builder=call(text,"begin",P.loc()).keep();
        while( true ) {
            if( part.text().length!=0 )
                call(text,"string",P.loc(),builder,P.allocations.newString(part.text()),P.con('s'),P.con(0),P.con(-1)).isKill();
            if( part.end() ) break;
            Lexer.Format fmt=P._lexer.stringFormat();
            Node value=P.expressions.parseAssignment().keep();
            P.require(")");
            Node fptr;
            if( fmt.kind()==0 ) {
                Node ints=P.references.member(text,"integer",fmt.site()).keep();
                Node floats=P.references.member(text,"floating",fmt.site()).keep();
                Node strings=P.references.member(text,"string",fmt.site()).keep();
                fptr=new FormatNode(value,ints.unkeep(),floats.unkeep(),strings.unkeep(),fmt.site()).peephole();
            } else {
                String family="diuoxXcb".indexOf(fmt.kind())>=0 ? "integer" : fmt.kind()=='s' ? "string" : "floating";
                fptr=P.references.member(text,family,fmt.site());
            }
            call(text,fptr,fmt.site(),builder,value.unkeep(),P.con(fmt.kind() | fmt.flags()<<8),
                 P.con(fmt.width()),P.con(fmt.precision())).isKill();
            part=P._lexer.stringPart(start);
        }
        // String syntax promises this result type even when sys.text is still
        // awaiting its source parse. Check/convert through the normal IR.
        Node result=call(text.unkeep(),"end",P.loc(),builder.unkeep());
        return P.conversions.convertExpr(result,TypeMemPtr.make(P.types.typeAry(TypeInteger.U8,true)).makeRO());
    }

    private Node call(Node text, String name, Lexer site, Node... values) {
        return call(text,P.references.member(text,name,site),site,values);
    }

    private Node call(Node text, Node fptr, Lexer site, Node... values) {
        Ary<Node> args=new Ary<>(Node.class);
        args.push(P.ctrl()); args.push(P.mem()); args.push(text);
        for( Node value:values ) args.push(value);
        args.push(fptr);
        return P.functions.functionCall(args,site);
    }
}
