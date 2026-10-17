package com.embers.legendcooldown;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LegendCooldown implements ClientModInitializer {
    public static final String ID = "legendcooldown";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    private static CooldownConfig config;
    public static CooldownConfig config() { return config; }

    @Override public void onInitializeClient() {
        config = CooldownConfig.load();
        ApolloCooldowns.init();
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "controls"));
        var settings = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.legendcooldown.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
        var editor = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.legendcooldown.editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ApolloCooldowns.tick();
            ApolloCooldowns.attachTeamviewer();
            while (settings.consumeClick()) {
                if (client.screen == null) client.setScreen(new CooldownSettingsScreen(null));
            }
            while (editor.consumeClick()) {
                if (client.screen == null && client.level != null) client.setScreen(new HudEditorScreen(null));
            }
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(ID, "cooldowns"),
                (graphics, delta) -> CooldownHud.drawHud(graphics));
        LOGGER.info("Legend Cooldown initialized");
    }
}
