package com.embers.legendcooldown;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

final class CooldownHud {
    private static final CooldownTimeCache TIME = new CooldownTimeCache();
    static void drawHud(GuiGraphics g) {
        var client = Minecraft.getInstance();
        var config = LegendCooldown.config();
        if (client.screen instanceof HudEditorScreen) return;
        if (client.player == null || client.options.hideGui || !config.enabled || !config.hudVisible) return;
        var rows = ApolloCooldowns.visible(false);
        if (rows.isEmpty()) return;
        drawRows(g, rows, false);
    }

    static HudLayout layout(List<ApolloCooldowns.Cooldown> rows) {
        var client = Minecraft.getInstance();
        var config = LegendCooldown.config();
        int cardWidth = config.cooldownStyle.equals("BAR") ? 140 : 100;
        if (config.cooldownShowNames) for (var row : rows)
            cardWidth = Math.max(cardWidth, Math.min(180, client.font.width(ApolloCooldowns.labelForRender(row)) + 12));
        int rowHeight = (config.cooldownStyle.equals("RING") ? 43 : 35) + (config.cooldownShowNames ? 12 : 0);
        return HudLayout.create(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(),
                cardWidth, rowHeight, rows.size(), config.scale, config.x, config.y, config.hudAnchorToScreen);
    }

    static void drawRows(GuiGraphics g, List<ApolloCooldowns.Cooldown> rows, boolean preview) {
        var client = Minecraft.getInstance();
        var config = LegendCooldown.config();
        var layout = layout(rows);
        int cardWidth = layout.cardWidth(), rowHeight = layout.rowHeight();
        var pose = g.pose();
        pose.pushMatrix(); pose.translate((float)layout.x(), (float)layout.y()); pose.scale(config.scale, config.scale);
        long now = System.nanoTime();
        int index = 0;
        for (var row : rows) {
            double fraction = preview ? .6 : row.fraction(now);
            int left = (index / layout.perColumn()) * (cardWidth + 3);
            int top = (index++ % layout.perColumn()) * rowHeight;
            if (config.background) g.fill(left, top, left + cardWidth, top + rowHeight - 3, 0x99000000);
            if (config.cooldownShowNames) {
                g.drawString(client.font, client.font.plainSubstrByWidth(ApolloCooldowns.labelForRender(row), cardWidth - 12), left + 6, top + 4, 0xFFFFFFFF, true);
                top += 12;
            }
            boolean ring = config.cooldownStyle.equals("RING");
            if (ring) for (int step = 0; step < 60; step++) {
                double angle = step * Math.PI / 30 - Math.PI / 2;
                int px = left + 22 + (int)Math.round(Math.cos(angle) * 18);
                int py = top + 21 + (int)Math.round(Math.sin(angle) * 18);
                g.fill(px - 1, py - 1, px + 1, py + 1, step < 60 * fraction ? 0xFFFFFFFF : 0x55666666);
            }
            if (!config.cooldownStyle.equals("TEXT")) {
                int ix = left + (ring ? 10 : 7), iy = top + (ring ? 9 : 5);
                var texture = ApolloCooldowns.textureForRender(row);
                if (texture != null) g.blit(texture, ix, iy, ix + 24, iy + 24, row.u0(), row.u1(), row.v0(), row.v1());
                else {
                    pose.pushMatrix(); pose.translate(ix, iy); pose.scale(1.5f, 1.5f);
                    var item = ApolloCooldowns.iconForRender(row, client);
                    g.renderItem(item.isEmpty() ? new ItemStack(Items.NETHER_STAR) : item, 0, 0);
                    pose.popMatrix();
                }
            }
            double seconds = preview ? row.durationNanos() / 1_000_000_000.0 * fraction : row.secondsLeft(now);
            g.drawString(client.font, TIME.time(row.name(), seconds, config.cooldownDecimals),
                    left + (config.cooldownStyle.equals("TEXT") ? 7 : ring ? 46 : 37), top + (ring ? 16 : 12), 0xFFFFFFFF, true);
            if (config.cooldownStyle.equals("BAR")) {
                g.fill(left + 37, top + 26, left + cardWidth - 6, top + 29, 0x55666666);
                g.fill(left + 37, top + 26, left + 37 + (int)Math.ceil((cardWidth - 43) * fraction), top + 29, 0xFFFFFFFF);
            }
        }
        pose.popMatrix();
    }
}
