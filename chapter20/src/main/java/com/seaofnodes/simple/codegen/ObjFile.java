package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.*;
import com.seaofnodes.simple.node.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedOutputStream;

public class ObjFile {

    final CodeGen _code;
    Ary<Section> _sections = new Ary<>(Section.class);

    ObjFile( CodeGen code ) { _code = code; }

    public static int writeCString(DataSection strtab, String name) {
        // place name in the string table
        int pos = strtab._contents.size();
        byte[] name_bytes = name.getBytes();
        strtab._contents.write(name_bytes, 0, name_bytes.length);
        // the strings are null terminated
        strtab._contents.write(0);
        return pos;
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

        Symbol(String name, int parent, int bind, int type) {
            _name = name;
            _parent = parent;
            _info = (bind << 4) | (type & 0xF);
        }

        void writeHeader(ByteBuffer out) {
            out.putInt(_name_pos);         // name
            out.put((byte) _info);         // info
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

    public static final int SECTION_HDR_SIZE = 64;
    abstract public static class Section {
        String _name;

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
            out.putLong(1);            // addralign
            if (_type == 2) {
                out.putLong(SYMBOL_SIZE);// entsize
            } else {
                out.putLong(0);         // entsize
            }
        }

        abstract void write(ByteBuffer out);
        abstract int size();
    }

    public static class SymbolSection extends Section {
        Ary<Symbol> _symbols = new Ary<>(Symbol.class);
        int _nonlocal_symbols;

        SymbolSection(String name, int type) {
            super(name, type);
        }

        @Override
        void writeHeader(ByteBuffer out) {
            // points to string table section
            _link = 1;

            // number of non-local symbols
            _info = _nonlocal_symbols;

            super.writeHeader(out);
        }

        void push(Symbol s) {
            if ((s._info >> 4) != SYM_BIND_LOCAL) {
                _nonlocal_symbols += 1;
            }

            s._index = _symbols.len();
            _symbols.push(s);
        }

        @Override
        void write(ByteBuffer out) {
            for( int i = 0; i < SYMBOL_SIZE/4; i++ ) {
                out.putInt(0);
            }

            for( Symbol s : _symbols ) {
                s.writeHeader(out);
            }
        }

        @Override
        int size() {
            return (1 + _symbols.len()) * SYMBOL_SIZE;
        }
    }

    public static class DataSection extends Section {
        ByteArrayOutputStream _contents = new ByteArrayOutputStream();

        DataSection(String name, int type) {
            super(name, type);
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

    private void encodeFunctions(SymbolSection symbols, DataSection text) {
        Symbol func = null;
        int func_start = 0;
        for( Node bb : _code._cfg ) {
            if (bb instanceof FunNode f) {
                // if there's a function we just finished building, let's close off the
                // symbol size
                if (func != null) {
                    func._size = text._contents.size() - func_start;
                    func_start = 0;
                }

                System.out.printf("FUNC %s\n", bb.toString());

                func = new Symbol(f._name, 3, SYM_BIND_GLOBAL, SYM_TYPE_FUNC);
                func._size = 0;
                func._value = text._contents.size();
                func_start = text._contents.size();
                symbols.push(func);
            }

            if (func != null) {
                // build up the encoding for this specific function
                for( Node n : bb.outs() ) {
                    if (n instanceof MachNode m) {
                        if( !(n instanceof ParmNode) ) {
                            System.out.println(m.getClass().getSimpleName());

                            // we should be calling "encoding" here
                            text._contents.write(0xCC);
                        }
                    }
                }
            }
        }

        if (func != null) {
            func._size = text._contents.size() - func_start;
            func_start = 0;
        }
    }

    public void export() {
        DataSection strtab = new DataSection(".strtab", 3);
        // first byte is reserved for an empty string
        strtab._contents.write(0);
        _sections.push(strtab);

        // place all the symbols
        SymbolSection symbols = new SymbolSection(".symtab", 2);
        _sections.push(symbols);

        DataSection text = new DataSection(".text", 1);
        text._flags = SHF_ALLOC | SHF_WRITE | SHF_EXECINSTR;
        _sections.push(text);

        System.out.println(_code.asm());
        encodeFunctions(symbols, text);

        // populate string table
        for( Section s : _sections ) { s._name_pos = writeCString(strtab, s._name); }
        for( Symbol s : symbols._symbols ) { s._name_pos = writeCString(strtab, s._name); }

        int idx = 1;
        for( Section s : _sections ) {
            Symbol sym = new Symbol(s._name, idx++, SYM_BIND_LOCAL, SYM_TYPE_SECTION);
            // we can reuse the same name pos from the actual section
            sym._name_pos = s._name_pos;
            sym._size = s.size();
            symbols.push(sym);
        }

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
        System.out.printf("A %d\n", out.position());

        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("a.o"));
            bos.write(out.array());
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
