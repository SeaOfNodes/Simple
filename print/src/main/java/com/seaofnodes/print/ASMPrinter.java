package com.seaofnodes.print;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import static com.seaofnodes.print.IRAdapter.Kind;

/** Assembly listing layout shared by every chapter with instruction selection. */
public final class ASMPrinter<N extends BaseNode<N>> {
    private final AssemblyAdapter<N> _a;
    private final IRAdapter<N> _ir;
    private final StringBuilder _text=new StringBuilder();
    private final IdentityHashMap<N,Boolean> _seen=new IdentityHashMap<>();
    private int _address;
    public ASMPrinter(AssemblyAdapter<N> adapter) { _a=adapter; _ir=adapter.ir; }

    public String print() {
        _text.setLength(0);
        _seen.clear();
        _address=0;
        ArrayList<N> blocks=_a.blocks();
        if( blocks.isEmpty() ) return "No scheduled code";
        for( int i=0; i<blocks.size(); i++ ) {
            N fun=blocks.get(i);
            if( _ir.kind(fun)!=Kind.FUN ) continue;
            _text.append("\n--- ").append(_ir.signature(fun)).append("---------------------------\n");
            if( _a.encoded() ) _address=(_address+15)&-16;
            if( _a.prologue(fun) ) instruction(fun,blocks,i);
            for( ; i<blocks.size() && _ir.kind(blocks.get(i))!=Kind.RETURN; i++ )
                block(blocks.get(i),blocks,i);
            _text.append("--- ").append(_ir.functionName(fun)).append("---------------------------\n");
        }
        for( Pool pool : _a.pools(_address) ) _text.append(pool(pool));
        return _text.toString();
    }

    private String label(N n) { return (_ir.kind(n)==Kind.LOOP ? "LOOP" : "L")+_ir.id(n); }
    private void block(N bb, ArrayList<N> blocks, int idx) {
        if( _a.labelBlock(bb) ) _text.append(label(bb)).append(":\n");
        if( _ir.kind(bb)==Kind.CALL ) return;
        boolean packed=false;
        for( int i=0; i<_ir.nOuts(bb); i++ ) {
            N n=_ir.out(bb,i);
            if( !_ir.phi(n) || _a.hidden(n) || _seen.put(n,Boolean.TRUE)!=null ) continue;
            if( _a.postAlloc() ) {
                if( !packed ) { indent(); packed=true; }
                _text.append(_a.phiName(n)).append(':').append(_a.register(n)).append(',');
            } else {
                indent(); field(_a.phiName(n),5); _text.append(' ').append(_a.register(n));
                if( _ir.kind(n)!=Kind.PARM ) {
                    _text.append(" = phi( ");
                    for( int j=1; j<_ir.nIns(n); j++ ) {
                        if( j>1 ) _text.append(',');
                        N def=_ir.in(n,j);
                        _text.append(def==null ? "___" : "N"+_ir.id(def));
                    }
                    _text.append(" )");
                }
                _text.append('\n');
            }
        }
        if( packed ) { _text.setLength(_text.length()-1); _text.append('\n'); }
        for( int i=0; i<_ir.nOuts(bb); i++ ) {
            N n=_ir.out(bb,i);
            if( n!=null && !_ir.phi(n) ) instruction(n,blocks,idx);
        }
    }

    private void instruction(N n, ArrayList<N> blocks, int idx) {
        if( n==null || _a.hidden(n) || _ir.kind(n)==Kind.CPROJ ) return;
        if( _ir.region(n) && _ir.kind(n)!=Kind.FUN ) {
            if( _a.encoded() ) return;
            // A successor may be reached by falling through empty blocks.
            for( int j=idx+1; j<blocks.size(); j++ ) {
                N next=blocks.get(j);
                if( next==n ) return;
                if( _ir.nOuts(next)>1 ) break;
            }
            address(_address++); field("??",_a.defaultSize()*2);
            _text.append("  "); field("JMP",5); _text.append(' ').append(label(n)).append('\n');
            return;
        }
        if( _seen.put(n,Boolean.TRUE)!=null ) return;
        if( _ir.kind(n)==Kind.PROJ ) {
            if( _a.encoded() ) return;
            indent(); _text.append("  "); field(_a.register(n),5);
            _text.append(" // ").append(_ir.label(n)).append('\n');
            return;
        }
        int offset=_a.offset(n), size=Math.max(0,_a.size(n)), width=_a.defaultSize();
        if( offset>=0 ) _address=offset;
        String args=_a.operands(n);
        String[] lines=(args.startsWith("\n") ? args.substring(1) : args).split("\n",-1);
        int done=0;
        for( int row=0; row<lines.length || done<size; row++ ) {
            if( row>0 && done>=size && row==lines.length-1 && lines[row].isEmpty() ) break;
            address(_address);
            int count=_a.bytes()==null ? 0 : Math.min(width,size-done);
            bytes(_address,count,_a.littleEndian());
            field("",(width-count)*2); _text.append("  ");
            if( row==0 ) { field(_a.op(n),5); _text.append(' '); }
            if( row<lines.length ) field(lines[row],row==0 ? 30 : 0);
            if( row==0 ) {
                String comment=_a.comment(n);
                if( comment!=null ) _text.append(" // ").append(comment);
            }
            _text.append('\n');
            done+=count; _address+=count;
            if( _a.bytes()==null ) {
                // Before encoding, multiline machine expansions still show
                // every instruction; byte lengths are not yet available.
                if( row==0 ) { _address+=size; done=size; }
            }
            if( count==0 && row>=lines.length-1 ) break;
        }
        if( !_ir.control(n) )
            for( int i=0; i<_ir.nOuts(n); i++ ) {
                N proj=_ir.out(n,i);
                if( proj!=null && _ir.kind(proj)==Kind.PROJ && _ir.input0(proj)==n ) instruction(proj,blocks,idx);
            }
    }
    private void indent() { field("",7+_a.defaultSize()*2); }
    private void field(String text, int width) {
        _text.append(text);
        if( width>text.length() ) _text.append(" ".repeat(width-text.length()));
    }
    private void address(int address) { _text.append("%04x ".formatted(address)); }
    private void bytes(int offset, int count, boolean little) {
        byte[] bytes=_a.bytes();
        for( int i=0; i<count; i++ ) {
            int idx=offset+(little ? i : count-i-1);
            if( idx<0 || idx>=bytes.length ) _text.append("??");
            else _text.append("%02x".formatted(bytes[idx]&255));
        }
    }

    /** Metadata captured during encoding; printing never computes a type's layout. */
    public record Data(Object identity, String type, int alignment, int size) {}
    public record Pool(String name, byte[] bytes, int offset, int address, ArrayList<Data> entries) {}

    public static String pool(Pool pool) {
        StringBuilder sb=new StringBuilder("--- "+pool.name()+" ------\n");
        ArrayList<Data> entries=new ArrayList<>(pool.entries());
        entries.sort(Comparator.comparingInt(Data::alignment).reversed());
        IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<>();
        int offset=pool.offset(), address=pool.address();
        for( Data entry : entries ) {
            if( seen.put(entry.identity(),Boolean.TRUE)!=null ) continue;
            sb.append("%04x  ".formatted(address));
            for( int i=0; i<entry.size(); i++ )
                sb.append(offset+i<pool.bytes().length ? "%02x".formatted(pool.bytes()[offset+i]&255) : "??");
            sb.append('\t').append(entry.type()).append('\n');
            offset+=entry.size(); address+=entry.size();
        }
        return sb.toString();
    }
}
