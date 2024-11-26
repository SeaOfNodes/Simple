package com.seaofnodes.simple;

import com.seaofnodes.simple.Parser;

abstract class TypeCheck {
    static void run(CodeGen code) {
        Parser.ParseException err = code._stop.walk(n -> {
            Parser.ParseException msg = n.err();
            if( msg != null && code._obs != null ) code._obs.error(n, msg.getMessage());
            return msg;
        });
        if( err != null )
            throw err;
    }
}
