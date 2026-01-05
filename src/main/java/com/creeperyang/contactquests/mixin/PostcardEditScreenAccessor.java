package com.creeperyang.contactquests.mixin;

import com.flechazo.contact.client.gui.screen.PostcardEditScreen;
import com.flechazo.contact.client.widget.EditableTextBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PostcardEditScreen.class)
public interface PostcardEditScreenAccessor {
    @Accessor(value = "textBox", remap = false)
    EditableTextBox contactQuests$getTextBox();
}