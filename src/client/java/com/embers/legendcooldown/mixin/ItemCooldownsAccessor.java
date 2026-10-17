package com.embers.legendcooldown.mixin;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ItemCooldowns.class)
public interface ItemCooldownsAccessor {
    @Accessor("tickCount") int legendcooldown$tickCount();
}
