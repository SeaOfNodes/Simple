package com.seaofnodes.simple;

import com.seaofnodes.simple.node.Node;

import java.util.regex.Pattern;

public class Reducer {

    private record Replace(Pattern p, String r) {}

    private static final Replace[] REPLACEMENTS = {
            new Replace(Pattern.compile("(?<=[+\\-*/<>=(])--"), ""),
            new Replace(Pattern.compile("\\b--"), "+"),
            new Replace(Pattern.compile("\\b\\+-"), "-"),
            new Replace(Pattern.compile("\\bfalse\\b"), "0"),
            new Replace(Pattern.compile("\\btrue\\b"), "1"),
            new Replace(Pattern.compile("-"), ""),
            new Replace(Pattern.compile("(?<![+\\-*/<>=])(?:-|\\*|/|>=|<=|>|<|!=|==)(?![+\\-*/<>=])"), "+"),
            new Replace(Pattern.compile("\\{(?:[^{}]|\\{})+}"), "{}"),
            new Replace(Pattern.compile("\\{((?:[^{}]|\\{})+)}"), "$1"),
            new Replace(Pattern.compile("\\(([^()]+)\\)"), "$1"),
            new Replace(Pattern.compile("\\b\\w+\\b"), "1"),
            new Replace(Pattern.compile("\\b\\w+\\b"), "0"),
            new Replace(Pattern.compile("\\d+\\+"), ""),
            new Replace(Pattern.compile("\\+\\d+"), ""),
            new Replace(Pattern.compile("(?<=\\n|^)[^\\n]*(?=\\n|$)"), "\n"),
            new Replace(Pattern.compile("\\n+"), "\n"),
            new Replace(Pattern.compile("^\\n+|\\n+$"), ""),
            new Replace(Pattern.compile("else "), ""),
            new Replace(Pattern.compile("\\bif\\([^()]+\\) *"), ""),
            new Replace(Pattern.compile("\\bwhile\\([^()]+\\) *"), ""),
    };

    private static boolean sameException(Throwable e1, Throwable e2) {
        if (e1 == null || e2 == null) return e1 == e2;
        if (e1.getClass() != e2.getClass()) return false;
        if (e1.getMessage() != null && e2.getMessage() != null && !e1.getMessage().equals(e2.getMessage())) return false;
        var s1 = e1.getStackTrace();
        var s2 = e2.getStackTrace();
        if (s1 == null || s1.length == 0 || s2 == null || s2.length == 0) return true;
        return s1[0].equals(s2[0]);
    }

    private final boolean disablePeeps;

    private Reducer(boolean disablePeeps) {
        this.disablePeeps = disablePeeps;
    }

    private Throwable parse(String script) {
        try {
            var parser = new Parser(script);
            Node._disablePeephole = disablePeeps;
            parser.parse();
            return null;
        } catch (Throwable e) {
            return e;
        }
    }

    private String doReplacement(String script, Throwable ex, Pattern pattern, String with) {
        var matcher = pattern.matcher(script);
        var sb = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            var pos = sb.length();
            matcher.appendReplacement(sb, with);
            var tail = sb.length();
            matcher.appendTail(sb);
            var ex2 = parse(sb.toString());
            if (sameException(ex, ex2)) {
                sb.setLength(tail);
            } else {
                sb.setLength(pos);
                sb.append(script, last, matcher.end());
            }
            last = matcher.end();
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String cleanVariables(String script, Throwable ex) {
        var matcher = Pattern.compile("\\bint ([0-9a-zA-Z_]+)").matcher(script);
        int num = 0;
        while (matcher.find()) {
            var var = matcher.group(1);
            if (var.matches("v\\d+")) continue;
            while (script.contains("v"+num)) num++;
            var n = script.replaceAll("\\b"+var+"\\b", "v" + num);
            var ex2 = parse(n);
            if (sameException(ex, ex2)) {
                script = n;
            }
        }
        return script;
    }

    private String doAllReplacements(String script, Throwable ex) {
        for (var replace: REPLACEMENTS) {
            script = doReplacement(script, ex, replace.p, replace.r);
        }
        return script;
    }

    public static String reduce(String script, boolean disablePeeps) {
        var reducer = new Reducer(disablePeeps);
        var ex = reducer.parse(script);
        if (ex == null) return "return 0;";
        String old;
        do {
            old = script;
            script = reducer.doAllReplacements(script, ex);
            script = reducer.cleanVariables(script, ex);
        } while (!script.equals(old));
        return script;
    }

}
