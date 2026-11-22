package com.creeperyang.contactquests.mixin;

import com.flechazo.contact.client.gui.screen.PostcardEditScreen;
import com.flechazo.contact.client.widget.EditableTextBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露 Contact 明信片编辑界面的正文输入框组件，供自动填充控制器逐字写入预设回信。 */
@Mixin(PostcardEditScreen.class)
public interface PostcardEditScreenAccessor {
    @Accessor(value = "textBox", remap = false)
    EditableTextBox contactQuests$getTextBox();
}