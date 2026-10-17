package com.embers.legendcooldown;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerCooldownTextTest {
    @Test void screenshotTimersAcceptArrowsAndResourcePackProgressGlyphs() {
        assertEquals("Skystrider",ServerCooldownText.read("Skystrider - 11.2s",true).name());
        assertEquals(11.2,ServerCooldownText.read("Skystrider – 11.2s",true).seconds());
        assertEquals(43.2, ServerCooldownText.read("Aegisblade → 43.2s", true).seconds());
        assertEquals("Veilblade", ServerCooldownText.read("§DVeilblade ➜ 30.8s\uE001\uE002", true).name());
        assertEquals(1.8, ServerCooldownText.arrowTimer("Creaking Smoke ➔ 1.8s\n\uE001\uE002").seconds());
        assertNull(ServerCooldownText.read("Aegisblade → 43.2s", false));
        assertNull(ServerCooldownText.arrowTimer("Aegisblade → 999999s"));
        assertNull(ServerCooldownText.arrowTimer("Boss health → 42"));
    }
    @Test void actualKingMcLoreSuffixAndBarrierEndAreRecognized() {
        assertEquals(45, ServerCooldownText.loreSeconds("45s Cooldown", false));
        assertEquals(32, ServerCooldownText.loreSeconds("§c32s Cooldown", false));
        assertEquals(1, ServerCooldownText.loreSeconds("1s Cooldown", false));
        assertEquals(-1, ServerCooldownText.loreSeconds("45s Cooldown", true));
        assertEquals(-1, ServerCooldownText.loreSeconds("Regeneration III (0:10)", false));
        assertNull(ServerCooldownText.read("Aegisblade barrier has begun!", false));
        assertEquals("Aegisblade", ServerCooldownText.read("Aegisblade barrier was cancelled.", false).name());
        assertEquals(ServerCooldownText.Kind.ACTIVATED, ServerCooldownText.read("Aegisblade barrier has ended.", false).kind());
    }
    @Test void smokeAbilityAssociationIsLimitedToTheObservedServer() {
        assertEquals("creakingscythe", ServerCooldownText.itemKey("Creaking Smoke", "kingmc.vn"));
        assertEquals("creakingscythe", ServerCooldownText.itemKey("Creaking Smoke", "KINGMC.VN:25565"));
        assertEquals("creakingsmoke", ServerCooldownText.itemKey("Creaking Smoke", "other.example"));
        assertEquals("creakingsmoke", ServerCooldownText.itemKey("Creaking Smoke", "notkingmc.vn"));
        assertEquals("aegisblade", ServerCooldownText.itemKey("Aegisblade", "kingmc.vn"));
    }
    @Test void actualKingMcMessagesRequireThePlayersOwnItem() {
        var ready = ServerCooldownText.read("\uDB80\uDC07 Your Petalfall is now available!", false);
        assertEquals(ServerCooldownText.Kind.READY, ready.kind());
        assertEquals("Petalfall", ready.name());
        assertEquals(ServerCooldownText.Kind.ACTIVATED, ServerCooldownText.read("You ate a Golden Head and gained Regeneration III (0:10) and Absorption II (2:00)!", false).kind());
        assertNull(ServerCooldownText.read("Creaking just ate Golden Head and all team members received the effect Regeneration III (0:03)", false));
        assertNull(ServerCooldownText.read("A Battle Royale Custom Game is now available!", false));
        assertNull(ServerCooldownText.read("ELIMINATION! Player was slain using Petalfall", false));
    }
    @Test void compactTimersAreActionBarOnlyAndNeedUnits() {
        assertEquals(12.5, ServerCooldownText.read("§dPetalfall: 12.5s", true).seconds());
        assertEquals(90, ServerCooldownText.read("Seekerbow: 1:30", true).seconds());
        assertNull(ServerCooldownText.read("Petalfall: 12.5s", false));
        assertNull(ServerCooldownText.read("Petalfall: 42", true));
        assertEquals(3, ServerCooldownText.read("Your Petalfall is on cooldown: 3s", false).seconds());
        assertEquals("", ServerCooldownText.read("You must wait 3 seconds before using this ability!", false).name());
    }
    @Test void staticLoreIsDistinctFromRemainingLore() {
        assertEquals(40, ServerCooldownText.loreSeconds("Cooldown: 40s", false));
        assertEquals(-1, ServerCooldownText.loreSeconds("Cooldown: 40s", true));
        assertEquals(12, ServerCooldownText.loreSeconds("Cooldown remaining: 12 seconds", true));
        assertEquals(0, ServerCooldownText.loreSeconds("Cooldown remaining: 0s", true));
        assertEquals(-1, ServerCooldownText.loreSeconds("Regeneration III (0:10)", false));
        assertEquals(-1, ServerCooldownText.loreSeconds("Cooldown: 1:99", false));
        assertNull(ServerCooldownText.read("Petalfall cooldown: 999999999s", false));
    }
    @Test void noDurationIsLearnedFromReadyAloneOrOtherItems() {
        var tracker = new ConfirmedCooldownTracker();
        assertEquals(0, tracker.ready("petalfall", 10_000_000_000L));
        tracker.activated("goldenhead", 15_000_000_000L);
        assertEquals(0, tracker.ready("seekerbow", 20_000_000_000L));
        assertEquals(5, tracker.ready("goldenhead", 20_000_000_000L));
        assertEquals(0, tracker.ready("goldenhead", 25_000_000_000L));
        tracker.activated("goldenhead", 30_000_000_000L); tracker.clear();
        assertEquals(0, tracker.ready("goldenhead", 35_000_000_000L));
    }
}
