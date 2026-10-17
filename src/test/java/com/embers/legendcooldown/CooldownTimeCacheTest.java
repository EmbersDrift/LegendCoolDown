package com.embers.legendcooldown;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CooldownTimeCacheTest {
    @Test void reusesTextWithinDisplayedTimeAndRefreshesBoundaries(){
        var cache=new CooldownTimeCache();
        String first=cache.time("Veilblade",31.91,true);
        assertSame(first,cache.time("Veilblade",31.92,true));
        assertEquals("31.9s",cache.time("Veilblade",31.89,true));
        assertEquals("32s",cache.time("Veilblade",31.89,false));
        assertEquals("1:30.1",cache.time("Veilblade",90.01,true));
        assertEquals("0.0s",cache.time("Veilblade",Double.NaN,true));
    }
    @Test void differentAbilitiesAndExpiredTimersKeepCorrectText(){
        var cache=new CooldownTimeCache();
        assertEquals("4.0s",cache.time("Aegisblade",4,true));
        assertEquals("1.8s",cache.time("Creaking Smoke",1.8,true));
        assertEquals("0.0s",cache.time("Aegisblade",0,true));
        cache.clear();
        assertEquals("45.0s",cache.time("Aegisblade",45,true));
    }
}
