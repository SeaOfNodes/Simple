package com.seaofnodes.simple.codegen;

import java.util.HashMap;
import java.util.Arrays;

// Dense IDs for this invocation. A class slot can be reserved before its file
// is parsed; every later reference to that slot gets the same ID.
public class LocalIds {
    public static final int RESERVED=2;
    private int _next=RESERVED;
    private final com.seaofnodes.simple.util.Ary<String> _owners=new com.seaofnodes.simple.util.Ary<>(String.class);
    private final HashMap<String,int[]> _slots=new HashMap<>();

    public int next(String owner) { return next(owner,-1); }
    public int next(String owner, int slot) {
        int[] ids=_slots.get(owner);
        if( ids==null ) ids=new int[]{RESERVED,1};
        if( slot==-1 ) slot=ids[0]++;
        else if( slot>=ids[0] ) ids[0]=slot+1;
        if( slot>=ids.length ) ids=Arrays.copyOf(ids,Math.max(slot+1,ids.length*2));
        if( ids[slot]==0 ) { ids[slot]=_next++; _owners.setX(ids[slot],owner); }
        _slots.put(owner,ids);
        return ids[slot];
    }
    public String owner(int id) { return _owners.atX(id); }
    public String symbol(int id) { return owner(id)+".$fun"+id; }
    public int nextInline() { return _next++; }
    public int next(int old) {
        int id=_next++;
        _owners.setX(id,owner(old));
        return id;
    }
}
