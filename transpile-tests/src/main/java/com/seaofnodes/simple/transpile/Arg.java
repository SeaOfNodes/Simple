package com.seaofnodes.simple.transpile;

public sealed interface Arg {
    record IntBot() implements Arg {
    }

    record IntTop() implements Arg {
    }

    record IntConstant(long value) implements Arg {
    }

    static Arg parse(String s) {
        if (s.equals("TypeInteger.BOT"))
            return new IntBot();
        if (s.equals("TypeInteger.TOP"))
            return new IntTop();
        var parts = s.split("\\(|\\)");
        if (parts.length != 2 | !parts[0].equals("TypeInteger.constant"))
            throw new RuntimeException("Unexpected Argument " + s);
        return new IntConstant(Long.parseLong(parts[1]));
    }
}
