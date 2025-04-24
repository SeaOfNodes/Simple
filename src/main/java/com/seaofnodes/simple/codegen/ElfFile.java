package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.codegen.Encoding.BAOS;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

public class ElfFile {

    final CodeGen _code;
    Ary<Section> _sections = new Ary<>(Section.class);

    ElfFile( CodeGen code ) { _code = code; }

    public static int writeCString(DataSection strtab, String name) {
        // place name in the string table
        int pos = strtab._contents.size();
        byte[] name_bytes = name.getBytes();
        strtab._contents.write(name_bytes, 0, name_bytes.length);
        // the strings are null terminated
        strtab._contents.write(0);
        return pos;
    }

    public static void write4( ByteArrayOutputStream bits, int op ) {
        bits.write(op    );
        bits.write(op>> 8);
        bits.write(op>>16);
        bits.write(op>>24);
    }
    public static void write8( ByteArrayOutputStream bits, long i64 ) {
        write4(bits, (int) i64     );
        write4(bits, (int)(i64>>32));
    }

    public static final int SYMBOL_SIZE = 4+1+1+2+8+8;

    public static final int SYM_BIND_LOCAL   = 0;
    public static final int SYM_BIND_GLOBAL  = 1;
    public static final int SYM_BIND_WEAK    = 2;

    public static final int SYM_TYPE_NOTYPE  = 0;
    public static final int SYM_TYPE_OBJECT  = 1;
    public static final int SYM_TYPE_FUNC    = 2;
    public static final int SYM_TYPE_SECTION = 3;

    public static class Symbol {
        String _name;
        int _name_pos;

        // index in the symbol table
        int _index;

        // which section is it in
        int _parent;

        // depends on the symbol type
        //   TODO
        int _value, _size;

        // top 4bits are the "bind", bottom 4 are "type"
        int _info;

        // bind 1011
        // 00001111
        // 10110000

        Symbol(String name, int parent, int bind, int type) {
            _name = name;
            _parent = parent;
            _info = (bind << 4) | (type & 0xF);
        }

        void writeHeader(ByteBuffer out) {
            out.putInt(_name_pos);         // name
            out.put((byte) _info);         // info
            // default visibility
            out.put((byte) 0);             // other
            out.putShort((short) _parent); // shndx
            out.putLong(_value);           // value
            out.putLong(_size);            // size
        }
    }

    public static final int SHF_WRITE     = 1;
    public static final int SHF_ALLOC     = 2;
    public static final int SHF_EXECINSTR = 4;
    public static final int SHF_STRINGS   = 32;
    public static final int SHF_INFO_LINK = 64;

    public static final int SECTION_HDR_SIZE = 64;
    abstract public static class Section {
        String _name;
        int _index;

        int _type, _flags;
        int _link, _info;

        // where's the name in the string table
        int _name_pos;
        int _file_offset;

        Section(String name, int type) {
            _name = name;
            _type = type;
        }

        void writeHeader(ByteBuffer out) {
            out.putInt(_name_pos);     // name
            out.putInt(_type);         // type
            out.putLong(_flags);       // flags
            out.putLong(0);            // addr
            out.putLong(_file_offset); // offset
            out.putLong(size());       // size
            out.putInt(_link);         // link
            out.putInt(_info);         // info
            out.putLong(16);           // addralign
            if (_type == 2) {
                out.putLong(SYMBOL_SIZE);// entsize
            } else if (_type == 4) {
                out.putLong(24);         // entsize
            } else {
                out.putLong(0);          // entsize
            }
        }

        abstract void write(ByteBuffer out);
        abstract int size();
    }

    public static class SymbolSection extends Section {
        Ary<Symbol> _symbols = new Ary<>(Symbol.class);
        Ary<Symbol> _loc = new Ary<>(Symbol.class);

        SymbolSection(String name, int type) {
            super(name, type);
        }

        @Override
        void writeHeader(ByteBuffer out) {
            // points to string table section
            _link = 1;

            // number of non-local symbols
            _info = _loc.len() + 1;

            super.writeHeader(out);
        }

        void push(Symbol s) {
            if ((s._info >> 4) != SYM_BIND_LOCAL) {
                _symbols.push(s);
            } else {
                _loc.push(s);
            }
        }

        @Override
        void write(ByteBuffer out) {
            // Index 0 both designates the first entry in the table and serves as the undefined symbol index
            for( int i = 0; i < SYMBOL_SIZE/4; i++ ) {
                out.putInt(0);
            }
            // index is already set
            for( Symbol s : _loc ) {
                s.writeHeader(out);
            }
            for( Symbol s : _symbols ) {
                s.writeHeader(out);
            }
        }

        @Override
        int size() {
            return (1 + _symbols.len() + _loc.len()) * SYMBOL_SIZE;
        }
    }

    public static class DataSection extends Section {
        ByteArrayOutputStream _contents;

        DataSection(String name, int type) {
            super(name, type);
            _contents = new ByteArrayOutputStream();
        }

        DataSection(String name, int type, ByteArrayOutputStream contents) {
            super(name, type);
            _contents = contents;
        }

        @Override
            void write(ByteBuffer out) {
            out.put(_contents.toByteArray());
        }

        @Override
            int size() {
            return _contents.size();
        }
    }

    private void pushSection(Section s) {
        _sections.push(s);
        // ELF is effectively "1-based", due to the NULL section
        s._index = _sections._len;
    }

    /* creates function and stores where it starts*/
    private final HashMap<TypeFunPtr,Symbol> _funcs = new HashMap<>();
    private void encodeFunctions(SymbolSection symbols, DataSection text) {
        for( int i=0; i<_code._cfg._len; i++ ) {
            if( !(_code._cfg.at(i) instanceof FunNode fun) ) continue;
            // skip until the function ends
            while( !(_code._cfg.at(i) instanceof ReturnNode ret) )
                i++;
            int end = _code._encoding._opStart[ret._nid] + _code._encoding._opLen[ret._nid];

            Symbol func = new Symbol(fun._name, text._index, SYM_BIND_GLOBAL, SYM_TYPE_FUNC);
            func._value = _code._encoding._opStart[fun._nid];
            func._size = end - func._value;
            symbols.push(func);
            _funcs.put(fun.sig(), func);
        }
    }

    public void export(String fname) throws IOException {
        DataSection strtab = new DataSection(".strtab", 3 /* SHT_SYMTAB */);
        // first byte is reserved for an empty string
        strtab._contents.write(0);
        pushSection(strtab);

        // place all the symbols
        SymbolSection symbols = new SymbolSection(".symtab", 2 /* SHT_STRTAB */);
        pushSection(symbols);

        // zero flag by default
        // we've already constructed this entire section in the encoding phase
        DataSection text = new DataSection(".text", 1 /* SHT_PROGBITS */, _code._encoding._bits);
        text._flags = SHF_ALLOC | SHF_WRITE | SHF_EXECINSTR;
        pushSection(text);

        // Build and write constant pool
        BAOS cpool = new BAOS();
        Encoding enc = _code._encoding;
        enc.writeConstantPool(cpool,false);
        DataSection rdata = new DataSection(".rodata", 1 /* SHT_PROGBITS */, cpool);
        rdata._flags = SHF_ALLOC;
        pushSection(rdata);

        // populate function symbols
        encodeFunctions(symbols, text);

        int idx = 1;
        for( Section s : _sections ) {
            Symbol sym = new Symbol(s._name, idx++, SYM_BIND_LOCAL, SYM_TYPE_SECTION);
            // we can reuse the same name pos from the actual section
            sym._name_pos = s._name_pos;
            sym._size = s.size();
            symbols.push(sym);
        }

        // calculate local index
        int num = 1;
        for( Symbol s : symbols._loc )
            s._index = num++;
        // extra space for .rela.text
        int start_global = num+1; // Add one to skip the final .rela.text local symbol
        for( Symbol a: symbols._symbols )
            a._index = start_global++;
        int bigConIdx = start_global;
        start_global += enc._bigCons.size();

        // create .text relocations
        DataSection text_rela = new DataSection(".rela.text", 4 /* SHT_RELA */);
        for( Node n : enc._externals.keySet()) {
            int nid    = n._nid;
            String extern = enc._externals.get(n);

            Symbol sym = new Symbol(extern, 0, SYM_BIND_GLOBAL, SYM_TYPE_NOTYPE);
            sym._index = start_global++;
            symbols.push(sym);

            int offset = enc._opStart[nid] + enc._opLen[nid] - 4;

            // u64 offset
            write8(text_rela._contents, offset);
            // u64 info
            write8(text_rela._contents, ((long)sym._index << 32L) | 4L /* PLT32 */);
            // i64 addend
            write8(text_rela._contents, -4);
        }

        // Write relocations for the constant pool
        for( Encoding.Relo relo : enc._bigCons.values() ) {
            Symbol glob = new Symbol("GLOB$"+bigConIdx, rdata._index, SYM_BIND_GLOBAL, SYM_TYPE_FUNC);
            glob._value = relo._target;
            glob._size = 1 << relo._t.log_size();
            glob._index = bigConIdx++;
            symbols.push(glob);
            write8(text_rela._contents, relo._opStart+relo._off);
            write8(text_rela._contents, ((long)glob._index << 32L) | relo._elf );
            write8(text_rela._contents, -4);
        }

        text_rela._flags = SHF_INFO_LINK;
        text_rela._link = 2;
        text_rela._info = text._index;
        pushSection(text_rela);

        Symbol sym = new Symbol(text_rela._name, num++, SYM_BIND_LOCAL, SYM_TYPE_SECTION);
        sym._name_pos = text_rela._name_pos;
        sym._size = text_rela.size();
        symbols.push(sym);

        // populate string table
        for( Section s : _sections ) { s._name_pos = writeCString(strtab, s._name); }
        for( Symbol s : symbols._symbols ) { s._name_pos = writeCString(strtab, s._name); }
        for( Symbol s : symbols._loc ) { s._name_pos = writeCString(strtab, s._name); }

        int size = 64; // ELF header
        // size of all section data
        for( Section s : _sections ) {
            s._file_offset = size;
            size += s.size();
        }
        // headers go at the very end
        int shdr_offset = size;
        // there's a "null" section at the start of the section headers
        size += SECTION_HDR_SIZE*(1 + _sections.len());

        // System.out.printf("Hello %d, %d, %s\n", size, shdr_offset);
        ByteBuffer out = ByteBuffer.allocate(size);
        out.order(ByteOrder.LITTLE_ENDIAN);

        // ELF header
        out.put(new byte[]{ 0x7F, 'E', 'L', 'F', 2, 1, 1, 0 });
        out.put(new byte[8]);
        out.putShort((short)1 /* ET_REL */); // type
        out.putShort((short)62 /* AMD64 */); // machine
        out.putInt(1);                // version
        out.putLong(0);               // entry
        out.putLong(0);               // phoff
        out.putLong(shdr_offset);     // shoff
        out.putInt(0);                // flags
        out.putShort((short)64);      // ehsize
        out.putShort((short)0);       // phentsize
        out.putShort((short)0);       // phnum
        out.putShort((short)64);      // shentsize
        out.putShort((short) (1 + _sections.len())); // shnum
        out.putShort((short)1);       // shstrndx

        // raw section data
        for( Section s : _sections ) {
            s.write(out);
        }

        // "null" section
        for( int i = 0; i < 16; i++ ) {
            out.putInt(0);
        }

        for( Section s : _sections ) {
            s.writeHeader(out);
        }
        assert out.position() == size;

        File file = new File(fname);
        if( file.getParentFile()!=null )
          file.getParentFile().mkdirs();
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
        bos.write(out.array());
        bos.close();
    }

}
