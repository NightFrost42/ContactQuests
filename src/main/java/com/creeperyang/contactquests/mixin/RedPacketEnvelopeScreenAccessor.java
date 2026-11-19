package com.creeperyang.contactquests.mixin;

import com.flechazo.contact.client.gui.screen.RedPacketEnvelopeScreen;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露 Contact 红包封套编辑界面的祝福语输入框，供红包任务自动填充预设祝福语。 */
@Mixin(RedPacketEnvelopeScreen.class)
public interface RedPacketEnvelopeScreenAccessor {
    @Accessor(value = "blessings", remap = false)
    EditBox contactQuests$getBlessingsBox();
}