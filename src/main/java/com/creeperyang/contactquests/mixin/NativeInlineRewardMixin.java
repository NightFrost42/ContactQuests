package com.creeperyang.contactquests.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ftb.mods.ftbquests.quest.loot.RewardTable;
import dev.ftb.mods.ftbquests.quest.reward.RandomReward;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 保留原生随机奖励中已配置的内嵌表标题，防止默认读取流程将其覆盖为 Internal。 */
@Mixin(RandomReward.class)
public abstract class NativeInlineRewardMixin {
    @WrapOperation(method = {"readData", "readNetData"}, at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbquests/quest/loot/RewardTable;setRawTitle(Ljava/lang/String;)V"), remap = false)
    private void retainConfiguredInlineTitle(RewardTable table, String defaultTitle, Operation<Void> original) {
        if (table.getRawTitle().isEmpty()) original.call(table, defaultTitle);
    }
}
