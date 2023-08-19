package com.seaofnodes.graph;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;

/** One local viewer session. HTTP assets and compiler messages share its lifetime. */
class GraphSocket extends ServerSocket {
    private DataInputStream _in;
    private OutputStream _out;
    private HttpServer _http;
    private Socket _client;

    GraphSocket(URI uri, int port) throws IOException {
        super(port, 1, InetAddress.getByName("127.0.0.1"));
        try {
            if( "file".equals(uri.getScheme()) ) {
                Path page = Path.of(uri);
                _http = SimpleFileServer.createFileServer(
                    new InetSocketAddress("127.0.0.1", 0), page.getParent(),
                    SimpleFileServer.OutputLevel.NONE);
                _http.start();
                uri = URI.create("http://127.0.0.1:" + _http.getAddress().getPort() + "/" + page.getFileName());
            }
            System.out.println("Graph viewer: " + uri);
            if( Boolean.parseBoolean(System.getProperty("simple.graph.open", "true")) )
                java.awt.Desktop.getDesktop().browse(uri);
            _client = accept();
            // Keep one buffer across the upgrade and all subsequent frames.
            _in = new DataInputStream(new BufferedInputStream(_client.getInputStream()));
            _out = new BufferedOutputStream(_client.getOutputStream());
            upgrade();
        } catch( IOException | RuntimeException | Error e ) {
            try { close(); } catch( IOException ignored ) {}
            throw e;
        }
    }

    private String line() throws IOException {
        var sb = new StringBuilder();
        for( int b; (b = _in.read()) != '\n'; ) {
            if( b == -1 ) throw new EOFException("Incomplete WebSocket upgrade");
            if( b != '\r' ) sb.append((char)b);
        }
        return sb.toString();
    }

    private void upgrade() throws IOException {
        String key = null;
        for( String line; !(line = line()).isEmpty(); ) {
            int colon = line.indexOf(':');
            if( colon > 0 && line.substring(0, colon).equalsIgnoreCase("Sec-WebSocket-Key") )
                key = line.substring(colon + 1).trim();
        }
        if( key == null ) throw new IOException("Missing WebSocket key");
        String accept;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(
                (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII));
            accept = Base64.getEncoder().encodeToString(hash);
        } catch( NoSuchAlgorithmException e ) {
            throw new IllegalStateException(e); // SHA-1 is required by the JDK.
        }
        _out.write(("HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: websocket\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        _out.flush();
    }

    public String get() throws IOException {
        ByteArrayOutputStream msg = null;
        while( true ) {
            int head = _in.read();
            if( head == -1 ) {
                if( msg != null ) throw new EOFException("Incomplete WebSocket message");
                return null;
            }
            boolean fin = (head & 128) != 0;
            int op = head & 15;
            int size = _in.readUnsignedByte();
            if( (head & 112) != 0 || (size & 128) == 0 )
                throw new IOException("Expected a masked WebSocket frame without extensions");
            long len = size & 127;
            if( len == 126 ) len = _in.readUnsignedShort();
            else if( len == 127 ) len = _in.readLong();
            if( len < 0 || len > Integer.MAX_VALUE ) throw new IOException("WebSocket frame too large");
            if( op >= 8 && (!fin || len > 125) ) throw new IOException("Invalid WebSocket control frame");
            byte[] mask = new byte[4];
            _in.readFully(mask);
            byte[] data = new byte[(int)len];
            _in.readFully(data); // TCP may deliver any header or payload in pieces.
            for( int i = 0; i < data.length; i++ ) data[i] ^= mask[i & 3];
            switch( op ) {
            case 8: return null;
            case 9: put(10, data); continue; // PONG echoes PING, even between fragments.
            case 10: continue;
            case 1:
                if( msg != null ) throw new IOException("Text frame inside an unfinished message");
                if( fin ) return new String(data, StandardCharsets.UTF_8);
                msg = new ByteArrayOutputStream();
                break;
            case 0:
                if( msg == null ) throw new IOException("Continuation without a text frame");
                break;
            default: throw new IOException("Unsupported WebSocket opcode: " + op);
            }
            msg.write(data);
            if( fin ) return msg.toString(StandardCharsets.UTF_8);
        }
    }

    public void put(String msg) throws IOException { put(1, msg.getBytes(StandardCharsets.UTF_8)); }

    private void put(int op, byte[] data) throws IOException {
        int len = data.length;
        _out.write(128 | op);
        if( len <= 125 ) _out.write(len);
        else if( len <= 65535 ) {
            _out.write(126);
            _out.write(len >>> 8);
            _out.write(len & 255);
        } else {
            _out.write(127);
            for( int shift = 56; shift >= 0; shift -= 8 )
                _out.write((int)((long)len >>> shift) & 255);
        }
        _out.write(data);
        _out.flush();
    }

    @Override public void close() throws IOException {
        try {
            if( _out != null && !isClosed() ) put(8, new byte[0]);
        } finally {
            if( _http != null ) _http.stop(0);
            try {
                if( _client != null ) _client.close();
            } finally {
                super.close();
            }
        }
    }
}
