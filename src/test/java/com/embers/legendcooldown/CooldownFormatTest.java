package com.embers.legendcooldown;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CooldownFormatTest {
    @Test void neverShowsZeroBeforeExpiry() {
        assertEquals("0.1s",CooldownFormat.time(0.001,true));
        assertEquals("1s",CooldownFormat.time(0.01,false));
        assertEquals("0.0s",CooldownFormat.time(0,true));
    }
    @Test void minuteDurationsAndDecimalBoundaries() {
        assertEquals("1:30.1",CooldownFormat.time(90.01,true));
        assertEquals("1:31",CooldownFormat.time(90.01,false));
        assertEquals("12.0s",CooldownFormat.time(12,true));
        assertEquals("0.0s",CooldownFormat.time(Double.NaN,true));
    }
    @Test void serverAndInventoryLabelsCanMatchWithoutFormattingCodes() {
        assertEquals(CooldownFormat.labelKey("§cMeteor Staff"),CooldownFormat.labelKey("meteor_staff-cooldown"));
        assertEquals("神器",CooldownFormat.labelKey("§b神器"));
    }
}
