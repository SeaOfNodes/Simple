package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.*;
import com.seaofnodes.simple.type.*;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;

public class ElfWriter {

    final CodeGen _code;
    final Ary<Section> _sections;
    StringSection _strtab;

    ElfWriter( CodeGen code ) {
        _code = code;
        _sections = new Ary<>(Section.class);
    }

    // ------------------------------------------------------------------------
    abstract public class Section {
        static final int SHT_NULL    = 0;
        static final int SHT_PROGBITS= 1;
        static final int SHT_SYMTAB  = 2;
        static final int SHT_STRTAB  = 3;
        static final int SHT_RELA    = 4;

        final String _name;
        final int _type, _entsize, _index, _name_pos, _flags;
        int _link, _info;
        int _file_offset;

        Section(String name, int type, int entsize, int flags) {
            _name = name;
            _type = type;
            _entsize = entsize;
            _flags = flags;
            _index = _sections._len;
            _sections.push(this);
            _name_pos = name.isEmpty()
                    ? 0  // Null section
                    : _strtab == null
                    ? 1  // StringSection
                    // All the other sections
                    : _strtab.writeCString(name);
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
            out.putLong(_entsize);     // Fixed-size element size
        }

        abstract void write(ByteBuffer out);
        abstract int size();
    }

    // ------------------------------------------------------------------------
    // Generic data sections; carries about a BAOS for bits
    public class DataSection extends Section {
        static final int SHF_WRITE     = 1;
        static final int SHF_ALLOC     = 2;
        static final int SHF_EXECINSTR = 4;
        static final int SHF_INFO_LINK = 64;

        final BAOS _contents;

        DataSection( String name, int type, int flags, int entsize ) { this(name, type, flags, entsize, new BAOS() ); }
        DataSection( String name, int type, int flags, BAOS contents ) { this(name, type, flags, 0, contents ); }
        private DataSection( String name, int type, int flags, int entsize, BAOS contents ) {
            super(name, type, entsize, flags);
            _contents = contents;
        }

        @Override void write(ByteBuffer out) { out.put(_contents.toByteArray()); }
        @Override int size() { return _contents.size(); }
        public void write2( int op ) {
            _contents.write(op    );
            _contents.write(op>> 8);
        }
        public void write4( int op ) {
            _contents.write(op    );
            _contents.write(op>> 8);
            _contents.write(op>>16);
            _contents.write(op>>24);
        }
        public void write8( long i64 ) {
            write4( (int) i64     );
            write4( (int)(i64>>32));
        }

    }

    // ------------------------------------------------------------------------
    // Section to collect and de-dup strings
    public class StringSection extends DataSection {
        final HashMap<String,Integer> _dedup = new HashMap<>();

        StringSection() {
            super(".strtab",SHT_STRTAB, 0, 0 );
            int emptyoff = writeCString("");   // Always empty string at offset 0
            assert emptyoff == 0;
            int namepos = writeCString(".strtab");
            assert namepos == _name_pos;
        }

        public int writeCString(String name) {
            Integer ii = _dedup.get(name);
            if( ii != null ) return ii;
            // place name in the string table
            int pos = _contents.size();
            _dedup.put(name,pos);
            byte[] name_bytes = name.getBytes();
            _contents.write(name_bytes);
            // the strings are null terminated
            _contents.write(0);
            return pos;
        }
    }

    // ------------------------------------------------------------------------
    // The relocations; 3 words per relocation
    public class ReloSection extends DataSection {
        ReloSection( int info ) {
            super(".rela.text", SHT_RELA, SHF_INFO_LINK, 24/*entsize*/);
            _link = 2;
            _info = info;
        }

        void writeReloOffPlus(int offset, int symidx, byte relo) {
            assert symidx > 0;
            write8(offset);
            write8(((long)symidx << 32L) | relo);
            write8(-4);
        }
    }


    // ------------------------------------------------------------------------
    // Symbols section; emit the symbols into the data section directly
    public class SymbolSection extends DataSection {
        public static final int SYMBOL_SIZE = 4+1+1+2+8+8;
        final BAOS _globals;
        final int _nlocals;

        SymbolSection(int nlocals) {
            super(".symtab", SHT_SYMTAB, 0/*flags*/, SYMBOL_SIZE);
            _globals = new BAOS();
            _nlocals = nlocals;
        }

        // Global symbol index
        int gidx() { return _nlocals+(_globals.size()/SYMBOL_SIZE); }

        public int symbol( String name, int shndx, int bind, int type, long value, long size ) {
            int gidx = gidx();  // Global index before I write to _globals
            BAOS baos = bind==SYM_BIND_GLOBAL ? _globals : _contents;
            write4(baos,_strtab.writeCString(name));
            int info = (bind << 4) | (type & 0xF);
            baos.write(info);
            baos.write(0);      // other/visibility
            write2(baos,shndx); // section index
            write8(baos,value);
            write8(baos,size);
            return bind==SYM_BIND_GLOBAL ? gidx : -99;
        }

        public int symbol( String name, int shndx, int bind, int type ) {
            return symbol(name,shndx,bind,type,0,0);
        }

        // creates function and stores where it starts
        void encodeFunctions(int text_idx) {
            for( Node ret : _code._stop._inputs ) {
                FunNode fun = ((ReturnNode)ret).fun();
                int end = _code._encoding._opStart[ret._nid] + _code._encoding._opLen[ret._nid];
                long value = _code._encoding._opStart[fun._nid];
                long size = end - value;
                symbol(fun._name, text_idx, SYM_BIND_GLOBAL, SYM_TYPE_FUNC, value, size);
            }
        }

        static void write2( BAOS bits, int op ) {
            bits.write(op    );
            bits.write(op>> 8);
        }
        static void write4( BAOS bits, int op ) {
            bits.write(op    );
            bits.write(op>> 8);
            bits.write(op>>16);
            bits.write(op>>24);
        }
        static void write8( BAOS bits, long i64 ) {
            write4(bits, (int) i64     );
            write4(bits, (int)(i64>>32));
        }

        @Override
        void writeHeader(ByteBuffer out) {
            // points to string table section
            _link = _strtab._index;
            _info = _nlocals;
            super.writeHeader(out);
        }

        @Override void write(ByteBuffer out) {
            out.put(_contents.toByteArray());
            out.put(_globals .toByteArray());
        }
        @Override int size() {
            return _contents.size() + _globals.size();
        }
    }

    public static final int SYM_BIND_LOCAL   = 0;
    public static final int SYM_BIND_GLOBAL  = 1;

    public static final int SYM_TYPE_NOTYPE  = 0;
    public static final int SYM_TYPE_OBJECT  = 1;
    public static final int SYM_TYPE_FUNC    = 2;
    public static final int SYM_TYPE_SECTION = 3;

    public static final int SECTION_HDR_SIZE = 64;

    // ------------------------------------------------------------------------
    public class SimpleSection extends DataSection {
        SimpleSection(BAOS serial) { super(".simple", 0x0A, 0, serial); }
    }


    // ------------------------------------------------------------------------

    public void export(String fname) {
        // Sections are created in the order they are emitted.

        // Null section
        Section nullSection = new DataSection("",Section.SHT_NULL,0,0);

        // String section next; written out early and available to record/dedup
        // strings for all remaining sections
        _strtab = new StringSection();

        // place all the symbols
        int nlocals = 1/*null*/+6/*number of sections*/+1/*Simple section*/;
        SymbolSection symbols = new SymbolSection(nlocals);

        // we've already constructed this entire section in the encoding phase
        DataSection text = new DataSection(".text",
                                           Section.SHT_PROGBITS, DataSection.SHF_ALLOC | DataSection.SHF_WRITE | DataSection.SHF_EXECINSTR,
                                           _code._encoding._bits );

        // Build and write constant pool
        BAOS cpool = new BAOS();
        Encoding enc = _code._encoding;
        enc.writeConstantPool(cpool,true,false);
        DataSection rodata = new DataSection(".rodata", Section.SHT_PROGBITS, DataSection.SHF_ALLOC, cpool );

        // Build and write r/w data 
        BAOS rwpool = new BAOS();
        enc.writeConstantPool(rwpool,false,false);
        if( rwpool.size() > 0 )
            throw Utils.TODO();
        DataSection rwdata = new DataSection(".data", Section.SHT_PROGBITS, DataSection.SHF_ALLOC, rwpool );

        
        // populate function symbols
        symbols.encodeFunctions(text._index);

        // create .text relocations
        ReloSection relocations = new ReloSection(text._index);

        for( Node n : enc._externals.keySet()) {
            String extern = enc._externals.get(n);
            int symidx = symbols.symbol(extern, 0, SYM_BIND_GLOBAL, SYM_TYPE_NOTYPE);
            int offset = enc._opStart[n._nid] + enc._opLen[n._nid] - 4;
            relocations.writeReloOffPlus(offset, symidx, (byte)4/*PLT32*/);
        }

        // Write relocations for the constant pool
        for( Encoding.Relo relo : enc._bigCons.values() ) {
            int symidx = symbols.symbol("CPOOL$"+symbols.gidx(), rodata._index, SYM_BIND_GLOBAL, SYM_TYPE_FUNC, relo._target, relo._t.size());
            relocations.writeReloOffPlus(relo._opStart+relo._off, symidx, relo._elf );
        }

        // Create a "Simple" section for Simple types and ir
        SimpleSection simple = new SimpleSection(_code._serial);

        // Write the local section header symbols
        assert nlocals == 1+_sections._len;
        symbols.symbol("",0,0,0); // Null symbol at index 0
        for( Section s : _sections )
            symbols.symbol(s._name, s._index, SYM_BIND_LOCAL, SYM_TYPE_SECTION, 0, s.size());

        int size = 64; // ELF header size
        // size of all section data
        for( Section s : _sections ) {
            s._file_offset = size;
            size += s.size();
        }
        // headers go at the very end
        int shdr_offset = size;
        size += SECTION_HDR_SIZE*_sections.len();

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
        out.putShort((short) _sections.len()); // shnum
        out.putShort((short) _strtab._index);  // shstrndx

        // raw section data
        for( Section s : _sections )
            s.write(out);

        // Section headers
        for( Section s : _sections )
            s.writeHeader(out);

        assert out.position() == size;

        File file = new File(fname);
        if( file.getParentFile()!=null )
            file.getParentFile().mkdirs();
        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
            bos.write(out.array());
            bos.close();
        } catch( IOException ioe ) {
            // Caller does not want to deal, but it is a failed compilation in any case
            throw new RuntimeException(ioe);
        }
    }

}
