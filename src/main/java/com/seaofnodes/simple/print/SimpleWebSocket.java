package com.seaofnodes.simple.print;

import com.seaofnodes.simple.util.Utils;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

// Why-Oh-Why is this needed?

// Pass in a URI and a port.
// Opens the client; client has to open a websocket at the given port.
// Connects, handshakes.
// Thereafter you can call get/put to swap frames with client.

class SimpleWebSocket extends ServerSocket {
    final  InputStream _in;
    final OutputStream _out;

    SimpleWebSocket(URI uri, int port) throws IOException, NoSuchAlgorithmException {
        super(port);
        // Launch client
        java.awt.Desktop.getDesktop().browse(uri);
        // Look for client; get in/out streams
        Socket sock = accept();
        _in  = sock. getInputStream();
        _out = sock.getOutputStream();


        // Establish web socket with handshake; easier with a buffered reader
        BufferedReader br = new BufferedReader(new InputStreamReader(_in));
        String key = null;
        while( br.ready() ) {
            String line = br.readLine();
            if( line.startsWith("Sec-WebSocket-Key: ") )
                key = line.substring(19);
        }
        assert key!=null;
        // Magic handshake
        byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                           + "Connection: Upgrade\r\n"
                           + "Upgrade: websocket\r\n"
                           + "Sec-WebSocket-Accept: "
                           + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes( StandardCharsets.UTF_8 )))
                           + "\r\n\r\n").getBytes( StandardCharsets.UTF_8 );
        _out.write(response,0,response.length);
    }

    final byte[] _ins = new byte[1024];
    final byte[] _str = new byte[1024];
    final byte[] _key = new byte[4];
    int _x, _len;
    private int rawget() { assert _x<_len; return _ins[_x++]&0xFF; }

    // TODO: Flip to Direct Byte Buffer, this is just a poor-mans version
    private void flipRead() throws IOException {
        // Move _x to _len to offset 0
        System.arraycopy(_ins,_x,_ins,0,(_len-_x));
        _len = _len-_x;
        _x=0;
        // Read more
        int len = _in.read(_ins,_len,_ins.length-_len);
        if( len == -1 )
            throw Utils.TODO(); // EOF
        _len += len;
    }
    // Block until you read at least len
    private void atLeast(int len) throws IOException {
        if( _x+len <= _len ) return;
        flipRead();
    }


    // Classic read from browser client.  Encoded, so decoded here
    public String get() throws IOException {
        atLeast(1);             // Need 1 byte for opcode
        int op = rawget();
        if( (op&0x80)!=0x80 )   // FIN bit not set?
            throw Utils.TODO( "Multi-part from client: " + op ); // Multipart message
        op &= 0x7F;             // Strip FIN bit
        switch( op&15) {
        case 1: {
            atLeast(5);         // Min message length
            // Message length
            int len = rawget()-128;
            if( len > 125 ) {
                if( len > 126 )
                    throw Utils.TODO(); // Multi-byte length
                len = (rawget()<<8) | rawget();
            }
            atLeast(len+4);     // Rest of message
            // Encoding key
            _key[0] = (byte)rawget();
            _key[1] = (byte)rawget();
            _key[2] = (byte)rawget();
            _key[3] = (byte)rawget();

            // Decode bytes
            for( int i=0; i<len; i++ )
                _str[i] = (byte)(rawget() ^ _key[i & 3]);

            return new String(_str,0,len);
        }
            // Client closes
        case 8: return null;
            // Ping
        case 9:
            // Send a PONG back.  TODO: echo PING data in the PONG
            _out.write(0x8A); // FIN + PONG
            _out.write(0x00); // LENGTH==0
            return get();
            // Not (yet) implemented: continuation frames, ping/pong
        default:
            throw Utils.TODO("Unexpected op from client: "+op); // Multi-part message
        }
    }

    // Classic put to browser client.  No encoding.
    public void put(String msg) throws IOException {
        int len = msg.length();
        if( len <= 125 ) {
            _out.write(129); // FIN, opcode 1 - whole text message
            _out.write(len);
            for( int i=0; i<msg.length(); i++ )
                _out.write(msg.charAt(i));
            return;
        }
        if( len > 65535 )
            throw Utils.TODO("MOAR LENGTH");
        _out.write(129);        // FIN, opcode 1 - whole text message
        _out.write(126);        // Length is 16b
        _out.write(len>>>8);
        _out.write(len&255);
        for( int i=0; i<msg.length(); i++ )
            _out.write(msg.charAt(i));
    }

    @Override public void close() throws IOException {
        if( _out != null ) {
            _out.write(0b10001000);
            _out.flush();
        }
        super.close();
    }
}
