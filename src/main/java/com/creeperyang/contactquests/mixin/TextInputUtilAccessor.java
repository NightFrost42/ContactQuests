package com.creeperyang.contactquests.mixin;

import net.minecraft.client.gui.font.TextFieldHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = TextFieldHelper.class)
public interface TextInputUtilAccessor {

    @Invoker("selectAll")
    void invokeSelectAll();

    @Invoker("insertText")
    void invokeInsertText(String text);
}