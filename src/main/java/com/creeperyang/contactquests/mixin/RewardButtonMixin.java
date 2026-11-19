package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.quest.reward.ParcelRewardBase;
import com.creeperyang.contactquests.quest.reward.PostcardReward;
import com.creeperyang.contactquests.quest.reward.RedPacketReward;
import com.creeperyang.contactquests.client.util.TooltipUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftblibrary.util.client.PositionedIngredient;
import dev.ftb.mods.ftbquests.client.gui.quests.RewardButton;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

/** 在 FTB 任务奖励按钮上渲染 Contact 邮件包裹与红包角标，并在右键菜单中附加快速配置项。 */
@Mixin(RewardButton.class)
public abstract class RewardButtonMixin {

    @Unique
    private static final Icon OVERLAY_PARCEL = Icon.getIcon("contact:item/parcel");
    @Unique
    private static final Icon OVERLAY_REDPACKET = Icon.getIcon("contact:item/red_packet");
    @Unique
    private static final Icon OVERLAY_ENDER_PARCEL = Icon.getIcon("contact:item/ender_parcel");
    @Shadow(remap = false)
    Reward reward;

    @Inject(method = "addMouseOverText", at = @At("HEAD"), cancellable = true, remap = false)
    private void concealMailRewardTooltip(TooltipList list, CallbackInfo ci) {
        if (reward instanceof ParcelRewardBase mail && mail.isContentsHiddenForViewer()) {
            list.add(Component.translatable("contactquests.reward.sealed"));
            list.add(Component.translatable("contactquests.reward.sealed_hint"));
            TooltipUtils.wrap(list);
            ci.cancel();
        }
    }

    @Inject(method = "getIngredientUnderMouse", at = @At("HEAD"), cancellable = true, remap = false)
    private void concealMailRewardIngredient(CallbackInfoReturnable<Optional<PositionedIngredient>> cir) {
        if (reward instanceof ParcelRewardBase mail && mail.isContentsHiddenForViewer()) cir.setReturnValue(Optional.empty());
    }

    @Inject(method = "addMouseOverText", at = @At("TAIL"), remap = false)
    private void wrapMailRewardTooltip(TooltipList list, CallbackInfo ci) {
        if (reward instanceof ParcelRewardBase) TooltipUtils.wrap(list);
    }

    @Inject(method = "draw", at = @At("TAIL"), remap = false)
    private void contactQuests_drawOverlay(GuiGraphics graphics, Theme theme, int x, int y, int w, int h, CallbackInfo ci) {
        if (!(reward instanceof ParcelRewardBase parcelReward) || reward instanceof PostcardReward) {
            return;
        }
        if (parcelReward.isContentsHiddenForViewer()) return;

        Icon overlayIcon;
        if (reward instanceof RedPacketReward) {
            overlayIcon = OVERLAY_REDPACKET;
        } else {
            overlayIcon = getOverlayIcon(parcelReward);
        }
        if (overlayIcon == null) return;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 300);

        RenderSystem.enableBlend();

        int size = (h >= 32 ? 16 : 10);
        int drawX = x + w + size - 29;
        int drawY = y + h - size;

        overlayIcon.draw(graphics, drawX, drawY, size, size);

        RenderSystem.disableBlend();
        pose.popPose();
    }

    @Unique
    private Icon getOverlayIcon(ParcelRewardBase reward) {
        boolean isEnder = reward.isEnder();

        return isEnder ? OVERLAY_ENDER_PARCEL : OVERLAY_PARCEL;

    }
}
