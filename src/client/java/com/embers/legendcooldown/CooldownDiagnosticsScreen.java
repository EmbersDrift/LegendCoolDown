package com.embers.legendcooldown;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CooldownDiagnosticsScreen extends Screen {
    private final Screen parent;
    private Button capture;
    public CooldownDiagnosticsScreen(Screen parent) { super(Component.translatable("screen.legendcooldown.diagnostics")); this.parent=parent; }
    private Component label() { return Component.translatable(CooldownDiagnostics.active()?"diagnostics.legendcooldown.stop":"diagnostics.legendcooldown.start"); }
    @Override protected void init() {
        int y=Math.max(70,height/2-30);
        capture=addRenderableWidget(Button.builder(label(),b->{if(CooldownDiagnostics.active())CooldownDiagnostics.stop();else CooldownDiagnostics.start();b.setMessage(label());}).bounds(width/2-150,y,300,20).build());
        addRenderableWidget(Button.builder(Component.translatable("diagnostics.legendcooldown.export"),b->CooldownDiagnostics.export()).bounds(width/2-150,y+24,148,20).build());
        addRenderableWidget(Button.builder(Component.translatable("diagnostics.legendcooldown.copy"),b->{var path=CooldownDiagnostics.lastExport();if(path!=null)minecraft.keyboardHandler.setClipboard(path.toString());}).bounds(width/2+2,y+24,148,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(width/2-75,y+78,150,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        super.render(g,mx,my,delta); capture.setMessage(label());
        int y=Math.max(70,height/2-30);
        g.drawCenteredString(font,title,width/2,y-57,0xFFFFFFFF);
        g.drawCenteredString(font,Component.translatable("diagnostics.legendcooldown.hint"),width/2,y-39,0xFFAAAAAA);
        g.drawCenteredString(font,Component.translatable("diagnostics.legendcooldown.status",CooldownDiagnostics.secondsLeft(),CooldownDiagnostics.count(),CooldownDiagnostics.dropped()),width/2,y-24,0xFFFFFFFF);
        g.drawCenteredString(font,font.plainSubstrByWidth(CooldownDiagnostics.exportStatus(),width-20),width/2,y+53,0xFFAAAAAA);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
