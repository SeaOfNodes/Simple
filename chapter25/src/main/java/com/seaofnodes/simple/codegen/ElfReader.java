package com.seaofnodes.simple.codegen;

import com.seaofnodes.simple.node.Node;
import com.seaofnodes.simple.type.TypeMemPtr;
import com.seaofnodes.simple.type.TypeStruct;
import com.seaofnodes.simple.util.Ary;
import com.seaofnodes.simple.util.BAOS;
import com.seaofnodes.simple.util.Utils;
import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;

public class ElfReader {

    public final File _file;
    public final byte[] _buf;
    int _x;                     // Cursor into buffer
    // ELF Header
    public final ElfHeader _header;
    // All sections
    public final Section[] _sections;
    // Simple section, if available
    public final Section _simple;
    // Byte array in/out stream - used as a reader
    public final BAOS _bais;

    // Strings from the Simple section, either small public set or whole set
    public String[] _strs;
    // Dependent file names
    public String[] _deps;
    // First N published symbols this file
    public Ary<TypeStruct> _published;

    // Load an Elf file (mostly lazily)
    public static ElfReader load( File f ) {
        try { return new ElfReader(f); }
        catch( IOException ioe ) { return null; }
    }

    // Load the Simple section from the ELF file
    private ElfReader( File f ) throws IOException {
        _file = f;
        _buf = Files.readAllBytes(f.toPath());
        _header = new ElfHeader();
        // No attempt is made to parse the program header table.
        _sections = new Section[_header._shnum];
        for( int i=0; i<_header._shnum; i++ )
            _sections[i] = new Section(i);

        // Load section names
        Section secstr = _sections[_header._shstrndx];
        for( Section sec : _sections )
            if( sec._type != 0 )
                sec._name = secstr.str(sec._noff);

        Section simple = null;
        for( Section sec : _sections )
            if( ".simple".equals(sec._name) )
                simple = sec;
        _simple = simple;
        _bais = new BAOS(_buf, (int)_simple._offset);
    }

    // Load the public symbols and dependent objs, but not the whole IR
    void loadPublicSymbols() {
        // Check already loaded and cached
        if( _strs == null && _simple != null )
            // Read only the published public strings, for speed purposes when
            // scanning a large count of ELF files but getting hits in only a
            // few of them.  i.e. optimizing (prematurely?) for the traditional
            // case of linking a small file against a large library
            Serialize.read_public_strs(this);
    }

    // Loads the entire IR
    Ary<TypeStruct> loadSimple() {
        assert _published == null;
        Serialize.readAll(this);
        return _published;
    }

    int u1() { return _buf[_x++]&0xFF; }
    int magic() {                         // Big endian
        return (u1() << 24) | (u1() << 16) | (u1() << 8) | u1();
    }
    int u2() { return u1() | (u1()<<8); } // Little endian
    int u4() {                  // Little endian
        return u1() | (u1() << 8) | (u1() << 16) | (u1() << 24);
    }
    long u8() { return u4() | ((long)u4()<<32); }
    void skip(int skip) { _x += skip; }


    public class ElfHeader {
        final int _magic;
        final boolean _format32; // Format32 or format64
        final byte _osabi;
        final boolean _little;  // True if little endian, false if big endian
        final char _type;
        final char _machine;
        final long _entry;      // Process entry point
        final long _phoff;      // Program header table offset, from file start
        final long _shoff;      // Section header table offset, from file start
        final int _flags;       // Flags
        final int _phnum;       // Number of program header entries
        final int _shentsize;
        final int _shnum;       // Number of section header entries
        final int _shstrndx;    // Section header index contains section names


        ElfHeader() throws IOException {
            _x = 0;
            if( (_magic = magic()) != 0x7F454C46 ) bad("magic number",_magic);
            byte EI_CLASS = (byte)u1();
            if( EI_CLASS != 1 && EI_CLASS != 2 ) bad("EI_CLASS",EI_CLASS);
            _format32 = (EI_CLASS==1);
            byte EI_DATA = (byte)u1();
            if( EI_DATA != 1 && EI_DATA != 2 ) bad("EI_DATA",EI_DATA);
            _little = EI_DATA == 1;
            if( u1() != 1 )  bad("version",1);
            if( (_osabi=(byte)u1()) != 0 )  bad("EI_VERSION",_osabi);
            byte abiversion = (byte)u1();
            if( abiversion != 0 ) bad("EI_ABIVERSION",abiversion);
            skip(7);            // Pad

            // Endian matters now
            if( !_little ) throw new IOException("TODO: Big endian");
            if( (_type = (char)u2()) != 1 )  bad("ET_REL",_type);
            if( (_machine = (char)u2()) != 0x3E )  bad("AMD x86-64",_machine);
            int ver = u4();
            if( ver != 1 ) bad("Version",ver);
            // Format32 matters now
            if( _format32 ) bad("Format",1);
            _entry = u8();      // Process entry point
            _phoff = u8();      // Program header table offset
            _shoff = u8();      // Section header table offset
            _flags = u4();      // Flags
            int ehsize = u2();
            if( ehsize != 0x40 ) bad("EHSIZE",ehsize);
            int phentsize = u2();
            if( phentsize != 0x38 && phentsize != 0 ) bad("PHENTSIZE",phentsize);
            _phnum = u2();
            _shentsize = u2();
            if( _shentsize != 0x40 ) bad("SHENTSIZE",_shentsize);
            _shnum = u2();
            // String section
            _shstrndx = u2();
            assert _x == ehsize;
        }
    }

    public class Section {
        final int _noff;        // Offset in string section for name
        final int _type;        // Section type
        final long _flags;
        final long _addr;       // Offset in virtual mem, if loaded
        final long _offset;     // Offset in file
        final long _size;       // Size in bytes
        final int _link;        // Associated section index, varies
        final int _info;        // Varies
        final byte _align;      // Log2 alignment
        final int _esize;       // Element size, if non-zero
        String _name;

        Section(int sidx) throws IOException {
            _x = (int)(_header._shoff + (sidx * _header._shentsize));
            _noff  = u4();
            _type  = u4();
            _flags = u8();
            _addr  = u8();
            _offset= u8();
            _size  = u8();
            _link  = u4();
            _info  = u4();
            long align = u8();
            if( align==0 ) align=1;
            _align = (byte)Long.numberOfTrailingZeros(align);
            if( (1L << _align ) != align )
                throw new IOException("Bad section alignment: "+align);
            _esize = (int)u8();
        }

        String str(int off) {
            assert _type == 3/*SHT_STRTAB*/; // String table section header
            int start = (int)(_offset+off);
            int x = start;
            while( _buf[x++] !=0 ) ;
            return new String(_buf,start,x-start-1);
        }
    }

    private static void bad(String msg, int val) throws IOException {
        throw new IOException("Bad "+msg+": "+val);
    }



    //public TypeMemPtr lookup( String name ) {
    //    if( _simple == null ) return null;
    //    BAOS baos = new BAOS(_buf, (int)_simple._offset);
    //    Ary<String> strs = Serialize.read_strs(baos);
    //
    //
    //
    //    Ary<Node> nodes = Serialize.read_types(baos);
    //
    //    throw Utils.TODO("Elf load symbol "+name);
    //}
}
