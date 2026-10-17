package com.embers.legendcooldown;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public final class CooldownSettingsScreen extends Screen {
    private final Screen parent;
    public CooldownSettingsScreen(Screen parent) { super(Component.translatable("screen.legendcooldown.settings")); this.parent = parent; }
    private int left() { return width / 2 - 155; }
    private int top() { return Math.max(27, (height - 178) / 2); }
    private Component state(String key, boolean value) { return Component.translatable(key).append(": ").append(Component.translatable(value ? "options.on" : "options.off")); }
    private void toggle(int column, int row, String key, BooleanSupplier get, Consumer<Boolean> set) {
        addRenderableWidget(Button.builder(state(key, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean()); LegendCooldown.config().save(); b.setMessage(state(key, get.getAsBoolean()));
        }).bounds(left() + column * 160, top() + row * 24, 150, 20).build());
    }
    @Override protected void init() {
        var c = LegendCooldown.config();
        toggle(0,0,"option.legendcooldown.enabled",()->c.enabled,v->c.enabled=v);
        addRenderableWidget(Button.builder(style(), b -> { c.cooldownStyle = CooldownFormat.nextStyle(c.cooldownStyle); c.save(); b.setMessage(style()); }).bounds(left()+160,top(),150,20).build());
        toggle(0,1,"option.legendcooldown.names",()->c.cooldownShowNames,v->c.cooldownShowNames=v);
        toggle(1,1,"option.legendcooldown.decimals",()->c.cooldownDecimals,v->c.cooldownDecimals=v);
        toggle(0,2,"option.legendcooldown.vanilla",()->c.cooldownVanilla,v->c.cooldownVanilla=v);
        toggle(1,2,"option.legendcooldown.sort",()->c.cooldownSortRemaining,v->c.cooldownSortRemaining=v);
        toggle(0,3,"option.legendcooldown.server",()->c.cooldownServerText,v->c.cooldownServerText=v);
        toggle(1,3,"option.legendcooldown.background",()->c.background,v->c.background=v);
        slider(0,4,"option.legendcooldown.x",c.x/100.0,0,100,v->c.x=v);
        slider(1,4,"option.legendcooldown.y",c.y/100.0,0,100,v->c.y=v);
        slider(0,5,"option.legendcooldown.scale",(c.scale-.5)/2.5,.5,3,v->c.scale=(float)v);
        addRenderableWidget(Button.builder(Component.translatable("screen.legendcooldown.diagnostics"), b -> minecraft.setScreen(new CooldownDiagnosticsScreen(this))).bounds(left()+160,top()+120,150,20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.legendcooldown.editor"), b -> minecraft.setScreen(new HudEditorScreen(this))).bounds(left(),top()+153,150,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left()+160,top()+153,150,20).build());
    }
    private Component style() { return Component.translatable("option.legendcooldown.style").append(": ").append(Component.translatable("style.legendcooldown."+LegendCooldown.config().cooldownStyle.toLowerCase(java.util.Locale.ROOT))); }
    private void slider(int col,int row,String key,double value,double min,double max,DoubleConsumer set) {
        var slider = new AbstractSliderButton(left()+col*160,top()+row*24,150,20,Component.empty(),value) {
            @Override protected void updateMessage() { setMessage(Component.translatable(key).append(": "+String.format(java.util.Locale.ROOT,"%.2f",min+value*(max-min)))); }
            @Override protected void applyValue() { set.accept(min+value*(max-min)); }
        };
        slider.setMessage(Component.translatable(key).append(": "+String.format(java.util.Locale.ROOT,"%.2f",min+value*(max-min))));
        addRenderableWidget(slider);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        super.render(g,mx,my,delta);
        g.drawCenteredString(font,title,width/2,top()-20,0xFFFFFFFF);
    }
    @Override public void removed() { LegendCooldown.config().save(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
