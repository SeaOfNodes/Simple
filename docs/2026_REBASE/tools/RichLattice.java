import com.seaofnodes.simple.type.*;
import java.nio.file.*;
import java.util.*;

/** Draw the ordering of Chapter 24's representative types and their duals. */
public class RichLattice {
    static String quote(String s) {
        return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";
    }
    static String color(Type t) {
        String name=t.getClass().getSimpleName();
        if(t==Type.TOP || t==Type.BOTTOM) return "#172e35";
        return switch(name) {
            case "TypeInteger" -> "#d3e7f5";
            case "TypeFloat" -> "#c7eee9";
            case "TypePtr", "TypeMemPtr" -> "#cce4d6";
            case "TypeFunPtr" -> "#dfd0ef";
            case "TypeMem" -> "#a9c9e5";
            case "TypeStruct", "Field" -> "#e0ebc8";
            case "TypeTuple" -> "#f2d7e4";
            case "TypeRPC" -> "#f4d4b5";
            default -> name.startsWith("TypeConAry") ? "#d9d5cc" : "#faedce";
        };
    }
    public static void main(String[] args) throws Exception {
        var types=new ArrayList<>(new LinkedHashSet<>(Arrays.asList(Type.gather())));
        int size=types.size();
        boolean[][] above=new boolean[size][size];
        for(int i=0;i<size;i++) for(int j=0;j<size;j++)
            above[i][j]=i!=j && types.get(i).meet(types.get(j))==types.get(j);
        int top=types.indexOf(Type.TOP), bottom=types.indexOf(Type.BOTTOM);
        for(int i=0;i<size;i++) {
            assert !above[i][top] && !above[bottom][i];
            assert i==top || above[top][i];
            assert i==bottom || above[i][bottom];
        }
        StringBuilder dot=new StringBuilder("digraph RichLattice {\n"+
            "graph [bgcolor=\"transparent\", ranksep=0.35, nodesep=0.12, pad=0.08];\n"+
            "node [shape=box, style=\"rounded,filled\", fontname=\"Arial\", fontsize=10, margin=\"0.06,0.04\", color=\"#647674\", penwidth=0.65];\n"+
            "edge [arrowhead=none, color=\"#899895\", penwidth=0.65];\n");
        for(int i=0;i<size;i++) {
            Type t=types.get(i);
            String name=t==Type.TOP?"TOP":t==Type.BOTTOM?"BOTTOM":t.str();
            dot.append("n").append(i).append(" [label=").append(quote(name))
                .append(", tooltip=").append(quote(t.getClass().getSimpleName()+": "+t))
                .append(", fillcolor=").append(quote(color(t)))
                .append(", fontcolor=").append(quote(t==Type.TOP || t==Type.BOTTOM?"white":"#172e35")).append("];\n");
        }
        int edges=0;
        for(int i=0;i<size;i++) for(int j=0;j<size;j++) if(above[i][j]) {
            boolean indirect=false;
            for(int k=0;k<size;k++) if(above[i][k] && above[k][j]) {indirect=true;break;}
            if(!indirect) {dot.append("n").append(i).append(" -> n").append(j).append(";\n");edges++;}
        }
        dot.append("}\n");
        Files.writeString(Path.of("docs/2026_REBASE/assets/rich-lattice.gv"),dot);
        System.out.println(size+" representative types; "+edges+" order edges (transitively reduced).");
    }
}
