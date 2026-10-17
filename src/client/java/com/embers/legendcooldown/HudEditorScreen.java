package com.embers.legendcooldown;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.Locale;

public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private List<ApolloCooldowns.Cooldown> preview = List.of();
    private HudLayout start;
    private double startX, startY;
    private Mode mode = Mode.NONE;
    private enum Mode { NONE, MOVE, SCALE }

    public HudEditorScreen(Screen parent) {
        super(Component.translatable("screen.legendcooldown.editor"));
        this.parent = parent;
    }
    @Override protected void init() {
        mode = Mode.NONE;
        if (preview.isEmpty()) {
            long now = System.nanoTime();
            preview = List.of(
                    new ApolloCooldowns.Cooldown("Legendary Sword", new ItemStack(Items.DIAMOND_SWORD), null,
                            0, 1, 0, 1, now + 12_000_000_000L, 20_000_000_000L),
                    new ApolloCooldowns.Cooldown("Artifact", new ItemStack(Items.NETHER_STAR), null,
                            0, 1, 0, 1, now + 24_000_000_000L, 40_000_000_000L));
        }
        addRenderableWidget(Button.builder(Component.translatable("editor.legendcooldown.reset"), b -> {
            var c = LegendCooldown.config(); c.x = 2; c.y = 25; c.scale = 1; c.hudAnchorToScreen = false; c.save();
        }).bounds(width / 2 - 155, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 + 5, height - 28, 150, 20).build());
    }
    HudLayout bounds() { return CooldownHud.layout(preview); }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x50000000);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        CooldownHud.drawRows(g, preview, true);
        var box = bounds();
        int x = (int)Math.floor(box.x()) - 1, y = (int)Math.floor(box.y()) - 1;
        int right = (int)Math.ceil(box.x() + box.width()) + 1;
        int bottom = (int)Math.ceil(box.y() + box.height()) + 1;
        int color = box.contains(mx, my) || mode != Mode.NONE ? 0xFFFFFFFF : 0xFFFFD166;
        g.fill(x, y, right, y + 1, color); g.fill(x, bottom - 1, right, bottom, color);
        g.fill(x, y, x + 1, bottom, color); g.fill(right - 1, y, right, bottom, color);
        g.fill(right - 5, bottom - 5, right + 3, bottom + 3, color);
        super.render(g, mx, my, delta);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("editor.legendcooldown.hint"), width / 2, 22, 0xFFDDDDDD);
        g.drawCenteredString(font, Component.translatable("editor.legendcooldown.status",
                (int)Math.round(box.x()), (int)Math.round(box.y()),
                String.format(Locale.ROOT, "%.0f%%", box.scale() * 100)), width / 2, height - 42, 0xFFFFFFFF);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        if (event.button() != 0) return false;
        var box = bounds();
        if (box.resizeHandle(event.x(), event.y())) mode = Mode.SCALE;
        else if (box.contains(event.x(), event.y())) mode = Mode.MOVE;
        else return false;
        start = box; startX = event.x(); startY = event.y();
        return true;
    }
    private void position(double x, double y) {
        var box = bounds(); var c = LegendCooldown.config();
        c.x = HudLayout.percent(Math.clamp(x, 0, Math.max(0, width - box.width())), width, 0);
        c.y = HudLayout.percent(Math.clamp(y, 0, Math.max(0, height - box.height())), height, 0);
        c.hudAnchorToScreen = true;
    }
    private void scale(double value, double anchorX, double anchorY, double u, double v) {
        var c = LegendCooldown.config();
        // Keep both preview rows visible, even when the GUI scale changes on a small window.
        var box = bounds();
        double limit = Math.min(3, Math.min(width / (double)box.cardWidth(), height / (double)(box.rowHeight() * preview.size())));
        c.scale = (float)Math.clamp(value, .5, Math.max(.5, limit));
        box = bounds();
        position(anchorX - u * box.width(), anchorY - v * box.height());
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (mode == Mode.NONE) return super.mouseDragged(event, dx, dy);
        if (mode == Mode.MOVE) position(start.x() + event.x() - startX, start.y() + event.y() - startY);
        else {
            double factor = 1 + ((event.x() - startX) * start.width() + (event.y() - startY) * start.height())
                    / Math.max(1, start.width() * start.width() + start.height() * start.height());
            scale(start.scale() * factor, start.x(), start.y(), 0, 0);
        }
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && mode != Mode.NONE) {
            mode = Mode.NONE; LegendCooldown.config().save(); return true;
        }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        var box = bounds();
        if (!box.contains(mx, my) || mode != Mode.NONE || vertical == 0)
            return super.mouseScrolled(mx, my, horizontal, vertical);
        scale(box.scale() * Math.pow(1.1, vertical), mx, my,
                (mx - box.x()) / box.width(), (my - box.y()) / box.height());
        LegendCooldown.config().save();
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { mode = Mode.NONE; LegendCooldown.config().save(); }
    @Override public void onClose() { LegendCooldown.config().save(); minecraft.setScreen(parent); }
}
