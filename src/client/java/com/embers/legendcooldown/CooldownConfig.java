package com.embers.legendcooldown;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CooldownConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("legendcooldown.json"); }
    public boolean enabled = true;
    public boolean hudVisible = true;
    public String cooldownStyle = "RING";
    public boolean cooldownVanilla = true;
    public boolean cooldownShowNames = false;
    public boolean cooldownDecimals = true;
    public boolean cooldownSortRemaining = false;
    public boolean cooldownServerText = true;
    public Map<String, Double> cooldownLearnedDurations = new LinkedHashMap<>();
    public double x = 2;
    public double y = 25;
    // Old configs use a percentage of free space. Mouse edits anchor to the screen instead.
    public boolean hudAnchorToScreen = false;
    public float scale = 1;
    public boolean background = true;

    static CooldownConfig load() {
        CooldownConfig config = null;
        if (Files.isRegularFile(path())) try {
            config = GSON.fromJson(Files.readString(path(), StandardCharsets.UTF_8), CooldownConfig.class);
        } catch (Exception error) { LegendCooldown.LOGGER.warn("Could not read cooldown config", error); }
        if (config == null) config = new CooldownConfig();
        config.sanitize();
        return config;
    }
    private void sanitize() {
        if (!List.of("RING", "ICON", "BAR", "TEXT").contains(cooldownStyle == null ? "" : cooldownStyle)) cooldownStyle = "RING";
        x = Double.isFinite(x) ? Math.clamp(x, 0, 100) : 2;
        y = Double.isFinite(y) ? Math.clamp(y, 0, 100) : 25;
        scale = Float.isFinite(scale) ? Math.clamp(scale, .5f, 3f) : 1;
        if (cooldownLearnedDurations == null) cooldownLearnedDurations = new LinkedHashMap<>();
        cooldownLearnedDurations.entrySet().removeIf(e -> e.getKey() == null || e.getKey().length() > 512
                || e.getValue() == null || !Double.isFinite(e.getValue()) || e.getValue() <= 0 || e.getValue() > 86400);
        while (cooldownLearnedDurations.size() > 256) cooldownLearnedDurations.remove(cooldownLearnedDurations.keySet().iterator().next());
    }
    void save() {
        sanitize();
        try {
            Files.createDirectories(path().getParent());
            Path temp = Files.createTempFile(path().getParent(), "legendcooldown-", ".tmp");
            try {
                Files.writeString(temp, GSON.toJson(this), StandardCharsets.UTF_8);
                try { Files.move(temp, path(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, path(), StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        } catch (Exception error) { LegendCooldown.LOGGER.warn("Could not save cooldown config", error); }
    }
}
