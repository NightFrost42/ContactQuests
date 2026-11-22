package com.creeperyang.contactquests.mixin;

import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露 FTB 原生物品奖励（ItemReward）中的随机浮动数量与限领唯一标记。 */
@Mixin(ItemReward.class)
public interface ItemRewardAccessor {
    @Accessor(value = "randomBonus", remap = false)
    int contactQuests$getRandomBonus();
    @Accessor(value = "onlyOne", remap = false)
    boolean contactQuests$isOnlyOne();
}
