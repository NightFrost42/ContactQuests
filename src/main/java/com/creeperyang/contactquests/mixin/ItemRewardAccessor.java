package com.creeperyang.contactquests.mixin;

import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemReward.class)
public interface ItemRewardAccessor {
    @Accessor(value = "randomBonus", remap = false)
    int contactquests$getRandomBonus();
}