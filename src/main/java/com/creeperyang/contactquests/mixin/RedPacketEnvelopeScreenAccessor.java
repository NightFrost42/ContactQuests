package com.creeperyang.contactquests.mixin;

import com.flechazo.contact.client.gui.screen.RedPacketEnvelopeScreen;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RedPacketEnvelopeScreen.class)
public interface RedPacketEnvelopeScreenAccessor {
    @Accessor("blessings")
    EditBox contactQuests$getBlessingsBox();
}