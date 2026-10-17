package com.embers.legendcooldown.mixin;
import com.embers.legendcooldown.ApolloCooldowns;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemCooldowns.class)
abstract class ItemCooldownsMixin {
    @Inject(method="addCooldown(Lnet/minecraft/resources/Identifier;I)V",at=@At("RETURN"))
    private void legendcooldown$started(Identifier group,int duration,CallbackInfo ci) {
        ApolloCooldowns.vanillaStarted((ItemCooldowns)(Object)this,group,duration);
    }
    @Inject(method="removeCooldown",at=@At("RETURN"))
    private void legendcooldown$ended(Identifier group,CallbackInfo ci) {
        ApolloCooldowns.vanillaEnded((ItemCooldowns)(Object)this,group);
    }
}
