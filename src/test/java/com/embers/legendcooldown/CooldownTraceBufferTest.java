package com.embers.legendcooldown;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CooldownTraceBufferTest {
    @Test void repeatsCannotFloodCaptureButChangedTimersAreRetained(){
        var buffer=new CooldownTraceBuffer(10);
        for(int i=0;i<100;i++)buffer.add(i,"gui-action-bar","Skystrider - 11.2s","Skystrider","accepted");
        assertEquals(1,buffer.size());
        buffer.add(100,"gui-action-bar","Skystrider - 11.1s","Skystrider","accepted");
        assertEquals(2,buffer.size());
        assertTrue(buffer.snapshot().getFirst().codepoints().contains("U+002D"));
    }
    @Test void diagnosticsHaveHardBoundsAndPreserveSupplementaryGlyphs(){
        var buffer=new CooldownTraceBuffer(2);
        buffer.add(0,"a","x".repeat(2000),"","parse-failed");
        buffer.add(0,"b","󰀇Skystrider","","item-not-found");
        buffer.add(0,"c","overflow","","accepted");
        assertEquals(2,buffer.size());assertEquals(1,buffer.dropped());
        assertEquals(1024,buffer.snapshot().getFirst().raw().length());
        assertTrue(buffer.snapshot().get(1).codepoints().contains("U+F0007"));
    }
}
