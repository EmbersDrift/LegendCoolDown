package com.embers.legendcooldown.mixin;

import com.embers.legendcooldown.ApolloCooldowns;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dedicated action-bar packets bypass Fabric's ChatListener game-message event. */
@Mixin(Gui.class)
public abstract class CooldownActionBarMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void legendcooldown$readActionBar(Component message, boolean animateColor, CallbackInfo ci) {
        ApolloCooldowns.actionBar(message);
    }
}
