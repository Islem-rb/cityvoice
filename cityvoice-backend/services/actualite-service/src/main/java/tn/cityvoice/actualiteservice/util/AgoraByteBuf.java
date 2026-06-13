package tn.cityvoice.actualiteservice.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Copie exacte du ByteBuf.java officiel Agora SDK.
 * Source : github.com/AgoraIO/Tools/DynamicKey/AgoraDynamicKey/java
 */
public class AgoraByteBuf {

    ByteBuffer buffer;

    public AgoraByteBuf() {
        this.buffer = ByteBuffer.allocate(128);
    }

    public AgoraByteBuf put(byte b) {
        this.buffer = this.ensureBuffer(1);
        this.buffer.put(b);
        return this;
    }

    public AgoraByteBuf put(short v) {
        this.buffer = this.ensureBuffer(2);
        this.buffer.order(ByteOrder.LITTLE_ENDIAN).putShort(v);
        return this;
    }

    public AgoraByteBuf put(int v) {
        this.buffer = this.ensureBuffer(4);
        this.buffer.order(ByteOrder.LITTLE_ENDIAN).putInt(v);
        return this;
    }

    /**
     * Écrit les bytes AVEC préfixe uint16(len).
     * Utilisé pour : appId, channelName, uid, signature.
     */
    public AgoraByteBuf put(byte[] bytes) {
        return this.put((short) bytes.length).put(bytes, bytes.length);
    }

    /**
     * Écrit les bytes BRUTS sans préfixe de longueur.
     */
    public AgoraByteBuf put(byte[] bytes, int len) {
        this.buffer = this.ensureBuffer(len);
        this.buffer.put(bytes, 0, len);
        return this;
    }

    /**
     * Écrit une String avec préfixe uint16(len).
     */
    public AgoraByteBuf put(String s) {
        try {
            return this.put(s.getBytes("UTF-8"));
        } catch (Exception e) {
            return this.put(s.getBytes());
        }
    }

    public byte[] asBytes() {
        return Arrays.copyOfRange(this.buffer.array(), 0, this.buffer.position());
    }

    private ByteBuffer ensureBuffer(int num) {
        if (this.buffer.remaining() >= num) {
            return this.buffer;
        }
        int newCapacity = Math.max(this.buffer.position() + num, this.buffer.capacity() * 2);
        ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity);
        this.buffer.flip();
        newBuffer.put(this.buffer);
        return newBuffer;
    }
}
