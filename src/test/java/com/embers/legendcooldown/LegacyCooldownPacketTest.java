package com.embers.legendcooldown;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyCooldownPacketTest {
    private byte[] packet(long millis) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes); byte[] name = "Petalfall".getBytes(StandardCharsets.UTF_8);
        out.writeByte(3); out.writeByte(name.length); out.write(name); out.writeLong(millis); out.writeInt(283);
        return bytes.toByteArray();
    }
    @Test void decodesPublicLegacyWireFormatAndCancel() throws Exception {
        var value = LegacyCooldownPacket.decode(packet(12_345));
        assertEquals("Petalfall", value.name()); assertEquals(12.345, value.seconds()); assertEquals(283, value.itemId());
        assertEquals(0, LegacyCooldownPacket.decode(packet(0)).seconds());
    }
    @Test void boundsMalformedUnrelatedAndNegativePackets() throws Exception {
        assertNull(LegacyCooldownPacket.decode(packet(-1)));
        assertNull(LegacyCooldownPacket.decode(packet(86_400_001)));
        assertNull(LegacyCooldownPacket.decode(new byte[]{3, 127}));
        assertNull(LegacyCooldownPacket.decode(new byte[]{4}));
        assertNull(LegacyCooldownPacket.decode(new byte[4097]));
        assertNull(LegacyCooldownPacket.decode(new byte[]{(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,3}));
    }
}
