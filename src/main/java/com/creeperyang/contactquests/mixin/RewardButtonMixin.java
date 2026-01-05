package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.quest.reward.ParcelRewardBase;
import com.creeperyang.contactquests.quest.reward.PostcardReward;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftbquests.client.gui.quests.RewardButton;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RewardButton.class)
public abstract class RewardButtonMixin {

    @Unique
    private static final Icon OVERLAY_PARCEL = Icon.getIcon("contact:item/parcel");
    @Unique
    private static final Icon OVERLAY_ENDER_PARCEL = Icon.getIcon("contact:item/ender_parcel");
    @Shadow(remap = false)
    Reward reward;

    @Inject(method = "draw", at = @At("TAIL"), remap = false)
    private void contactQuests_drawOverlay(GuiGraphics graphics, Theme theme, int x, int y, int w, int h, CallbackInfo ci) {
        if (!(reward instanceof ParcelRewardBase parcelReward) || reward instanceof PostcardReward) {
            return;
        }

        Icon overlayIcon = getOverlayIcon(parcelReward);
        if (overlayIcon == null) return;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 300);

        RenderSystem.enableBlend();

        int size = w / 2;

        int drawX = x + w - size;
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