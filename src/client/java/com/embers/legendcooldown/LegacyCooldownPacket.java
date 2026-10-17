package com.embers.legendcooldown;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Decoder for the public BukkitAPI-NetHandler cooldown packet, without its dependencies. */
final class LegacyCooldownPacket {
    record Value(String name, double seconds, int itemId) {}
    static Value decode(byte[] bytes) {
        if (bytes == null || bytes.length > 4096) return null;
        try {
            ByteBuffer input = ByteBuffer.wrap(bytes);
            if (varInt(input) != 3) return null;
            int length = varInt(input);
            if (length < 1 || length > 512 || input.remaining() < length + 12) return null;
            byte[] name = new byte[length]; input.get(name);
            long millis = input.getLong(); int icon = input.getInt();
            String label = new String(name, StandardCharsets.UTF_8);
            if (label.isBlank() || label.length() > 128 || millis < 0 || millis > 86_400_000) return null;
            return new Value(label, millis / 1000.0, icon);
        } catch (RuntimeException ignored) { return null; }
    }
    private static int varInt(ByteBuffer input) {
        int result = 0;
        for (int i = 0; i < 5; i++) {
            int part = input.get() & 255; result |= (part & 127) << (7 * i);
            if ((part & 128) == 0) return result;
        }
        throw new IllegalArgumentException("Oversized VarInt");
    }
}
