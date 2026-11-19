package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.quest.reward.ParcelRewardBase;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在 FTB 缓存自定义标题、图标与主题回退显示前，对未揭示内容的盲盒包裹奖励执行遮蔽。 */
@Mixin(QuestObjectBase.class)
public abstract class MailRewardPresentationMixin {
    @Inject(method = "getIcon", at = @At("HEAD"), cancellable = true, remap = false)
    private void sealedIcon(CallbackInfoReturnable<Icon> cir) {
        if ((Object) this instanceof ParcelRewardBase reward && reward.isContentsHiddenForViewer()) {
            cir.setReturnValue(reward.sealedIcon());
        }
    }

    @Inject(method = "getTitle", at = @At("HEAD"), cancellable = true, remap = false)
    private void sealedTitle(CallbackInfoReturnable<Component> cir) {
        if ((Object) this instanceof ParcelRewardBase reward && reward.isContentsHiddenForViewer()) {
            cir.setReturnValue(Component.translatable("contactquests.reward.sealed"));
        }
    }
}
