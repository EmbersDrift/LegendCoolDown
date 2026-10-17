package com.embers.legendcooldown;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import com.embers.legendcooldown.mixin.BossHealthOverlayAccessor;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Fallback for servers that publish cooldowns as system text or changing item lore. */
final class ServerArtifactCooldowns {
    private static final ConfirmedCooldownTracker TRACKER = new ConfirmedCooldownTracker();
    private static final Map<String, ItemStack> ICONS = new LinkedHashMap<>();
    private static final Set<String> REPORTED = new HashSet<>();
    private static final Map<Integer, Integer> LORE_SIGNATURES = new LinkedHashMap<>();
    private static final Map<UUID, String> BOSS_TITLES = new LinkedHashMap<>();
    private static ItemStack lastUsed = ItemStack.EMPTY;
    private static long lastUsedAt;
    private static int ticks;
    static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> receive(message.getString(), overlay));
    }
    static void clear() {
        TRACKER.clear(); ICONS.clear(); REPORTED.clear(); LORE_SIGNATURES.clear(); BOSS_TITLES.clear();
        lastUsed = ItemStack.EMPTY; lastUsedAt = 0; ticks = 0;
    }
    static void used(ItemStack stack) {
        CooldownDiagnostics.used(stack);
        if (!stack.isEmpty()) {
            if(stack.getCount()!=lastUsed.getCount() || !ItemStack.isSameItemSameComponents(stack,lastUsed))lastUsed=stack.copy();
            lastUsedAt=System.nanoTime();
        }
    }
    static void receive(String text, boolean overlay) {
        receive(text, overlay, overlay ? "fabric-overlay" : "system-message");
    }
    static void receive(String text, boolean overlay, String source) {
        Minecraft client = Minecraft.getInstance();
        var signal = ServerCooldownText.read(text, overlay);
        if (client.player == null || !LegendCooldown.config().cooldownServerText) {
            if(overlay || signal!=null)CooldownDiagnostics.record(source,text,signal,client.player==null?"no-player":"server-text-disabled");
            return;
        }
        if(overlay || signal!=null)CooldownDiagnostics.record(source,text,signal,signal==null?"parse-failed":"parsed");
        if (signal == null) {
            String clean = ServerCooldownText.clean(text);
            if (overlay && (clean.toLowerCase(java.util.Locale.ROOT).contains("cooldown") || clean.contains("冷却")))
                report("unknown-overlay:" + Math.floorMod(clean.hashCode(), 4), "Unrecognized cooldown action bar: " + clean.substring(0, Math.min(160, clean.length())));
            return;
        }
        if (signal.kind() == ServerCooldownText.Kind.REMAINING) {
            timer(signal, serverAddress(client), source);
            return;
        }
        long now = System.nanoTime();
        String name = signal.name();
        if (name.isBlank() && now - lastUsedAt <= 3_000_000_000L && !lastUsed.isEmpty()) name = lastUsed.getHoverName().getString();
        if (name.isBlank()) return;
        String key = CooldownFormat.labelKey(name);
        if (key.isBlank()) return;
        if (signal.kind() == ServerCooldownText.Kind.READY) {
            ApolloCooldowns.removeServerCooldown(name);
            double learned = TRACKER.ready(key, now);
            if (learned > 0) {
                var settings = LegendCooldown.config();
                settings.cooldownLearnedDurations.put(profileKey(client, key), learned);
                while (settings.cooldownLearnedDurations.size() > 256)
                    settings.cooldownLearnedDurations.remove(settings.cooldownLearnedDurations.keySet().iterator().next());
                settings.save();
                report("learn:" + key, "Confirmed cooldown duration learned for " + name + ": " + learned + "s");
            } else {
                report("ready:" + key, "Cooldown ready signal for " + name + "; no confirmed start/duration yet");
                ItemStack known = findIcon(client, key, now);
                var lore = known.get(DataComponents.LORE);
                if (lore != null) for (var line : lore.lines()) {
                    String hint = ServerCooldownText.clean(line.getString());
                    if (hint.toLowerCase(java.util.Locale.ROOT).contains("cooldown") || hint.contains("冷却"))
                        report("hint:" + key, "Artifact cooldown lore for " + name + ": " + hint.substring(0, Math.min(160, hint.length())));
                }
            }
            ICONS.remove(key);
            return;
        }
        ItemStack icon = findIcon(client, key, now);
        // Require a named item owned/used by this player before accepting a text timer.
        if (icon.isEmpty()) { CooldownDiagnostics.record(source,text,signal,"activation-item-not-found key="+key);return; }
        ICONS.put(key, icon.copy());
        while (ICONS.size() > 32) ICONS.remove(ICONS.keySet().iterator().next());
        {
            TRACKER.activated(key, now);
            double duration = totalFromLore(icon);
            if (duration <= 0) duration = LegendCooldown.config().cooldownLearnedDurations.getOrDefault(profileKey(client, key), 0.0);
            if (duration > 0) ApolloCooldowns.serverCooldown(name, duration, icon, true);
            report("use:" + key, "Confirmed artifact activation for " + name + (duration > 0 ? "; duration " + duration + "s" : "; waiting for server duration"));
        }
    }
    private static String serverAddress(Minecraft client) {
        return client.getCurrentServer() == null ? "" : client.getCurrentServer().ip;
    }
    static boolean timer(ServerCooldownText.Signal signal, String serverAddress, String source) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !LegendCooldown.config().cooldownServerText || signal == null
                || signal.kind() != ServerCooldownText.Kind.REMAINING) return false;
        String name = signal.name();
        long now = System.nanoTime();
        if (name.isBlank() && now - lastUsedAt <= 3_000_000_000L && !lastUsed.isEmpty())
            name = lastUsed.getHoverName().getString();
        String key = CooldownFormat.labelKey(name);
        if (key.isBlank()) {CooldownDiagnostics.record(source,signal.toString(),signal,"empty-item-key");return false;}
        boolean continuing=ApolloCooldowns.serverTimerActive(name);
        ItemStack icon = continuing ? ICONS.get(key) : null;
        String itemKey = ServerCooldownText.itemKey(name, serverAddress);
        if (icon == null || icon.isEmpty()) icon = findIcon(client, itemKey, now,!continuing);
        if (icon.isEmpty()) {CooldownDiagnostics.record(source,signal.toString(),signal,"item-not-found-or-ambiguous key="+itemKey);return false;}
        if(!continuing || !ICONS.containsKey(key))ICONS.put(key, icon.copy());
        while (ICONS.size() > 32) ICONS.remove(ICONS.keySet().iterator().next());
        // An ability's duration can differ from the parent weapon's cooldown.
        double total = key.equals(CooldownFormat.labelKey(icon.getHoverName().getString())) ? totalFromLore(icon) : 0;
        ApolloCooldowns.serverCooldown(name, signal.seconds(), icon, false, total);
        if(CooldownDiagnostics.active()){
            var config=LegendCooldown.config();
            CooldownDiagnostics.record(source,signal.toString(),signal,"accepted; item="+icon.getHoverName().getString()+
                    "; HUD="+config.hudVisible+"; widget="+config.enabled);
        }
        String reportKey=source + ":" + key;
        if(!REPORTED.contains(reportKey))report(reportKey, "Cooldown received from " + source + " for " + name + ": " + signal.seconds() + "s");
        return true;
    }
    private static void bossTimers(Minecraft client) {
        var events = ((BossHealthOverlayAccessor)client.gui.getBossOverlay()).legendcooldown$events();
        BOSS_TITLES.keySet().retainAll(events.keySet());
        int scanned = 0;
        for (var event : events.entrySet()) {
            if (++scanned > 64) break;
            String title = event.getValue().getName().getString();
            if (title.equals(BOSS_TITLES.put(event.getKey(), title))) continue;
            var signal=ServerCooldownText.arrowTimer(title);
            CooldownDiagnostics.record("boss-bar",title,signal,signal==null?"parse-failed":"parsed");
            timer(signal, serverAddress(client), "boss-bar");
        }
    }
    private static String profileKey(Minecraft client, String key) {
        var server = client.getCurrentServer();
        return (server == null ? "singleplayer" : server.ip.toLowerCase(java.util.Locale.ROOT)) + "|" + key;
    }
    static ItemStack iconForName(String name) {
        Minecraft client = Minecraft.getInstance();
        return client.player == null ? ItemStack.EMPTY : findIcon(client, CooldownFormat.labelKey(name), System.nanoTime());
    }
    private static ItemStack findIcon(Minecraft client, String key, long now) {
        return findIcon(client,key,now,false);
    }
    private static ItemStack findIcon(Minecraft client, String key, long now,boolean ignoreCache) {
        if (now - lastUsedAt <= 10_000_000_000L && key.equals(CooldownFormat.labelKey(lastUsed.getHoverName().getString()))) return lastUsed;
        ItemStack cached = ignoreCache ? null : ICONS.get(key);
        if (cached != null) return cached;
        ItemStack found = ItemStack.EMPTY;
        var inventory = client.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && key.equals(CooldownFormat.labelKey(stack.getHoverName().getString()))) {
                if (!found.isEmpty() && !ItemStack.isSameItemSameComponents(found, stack)) return ItemStack.EMPTY;
                found = stack;
            }
        }
        return found;
    }
    private static double totalFromLore(ItemStack stack) {
        var lore = stack.get(DataComponents.LORE);
        if (lore == null) return 0;
        for (var line : lore.lines()) {
            double duration = ServerCooldownText.loreSeconds(line.getString(), false);
            if (duration > 0) return duration;
        }
        return 0;
    }
    static void tick(Minecraft client) {
        if (client.player == null || !LegendCooldown.config().cooldownServerText) return;
        bossTimers(client);
        if (++ticks % 5 != 0) return;
        var inventory = client.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            var lore = stack.get(DataComponents.LORE);
            if (stack.isEmpty() || lore == null) { LORE_SIGNATURES.remove(i); continue; }
            int signature = ItemStack.hashItemAndComponents(stack);
            Integer previous = LORE_SIGNATURES.put(i, signature);
            // An unchanged line must not continually restart its timer.
            if (previous != null && previous == signature) continue;
            for (var line : lore.lines()) {
                double remaining = ServerCooldownText.loreSeconds(line.getString(), true);
                if (remaining >= 0) {
                    String name = stack.getHoverName().getString();
                    ApolloCooldowns.serverCooldown(name, remaining, stack);
                    report("lore:" + CooldownFormat.labelKey(name), "Cooldown received from changing item lore for " + name);
                    break;
                }
            }
        }
    }
    static void report(String key, String message) {
        if (REPORTED.size() < 64 && REPORTED.add(key)) LegendCooldown.LOGGER.info("[Artifact cooldowns] {}", message);
    }
}
