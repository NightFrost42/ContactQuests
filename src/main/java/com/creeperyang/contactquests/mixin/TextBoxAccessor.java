package com.creeperyang.contactquests.mixin;

import com.flechazo.contact.client.widget.EditableTextBox;
import net.minecraft.client.gui.font.TextFieldHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = EditableTextBox.class, remap = false)
public interface TextBoxAccessor {

    @Accessor("textInputUtil")
    TextFieldHelper getTextInputUtil();

    @Accessor("page")
    void setPage(String text);

    @Accessor("isModified")
    void setIsModified(boolean modified);

    @Invoker("shouldRefresh")
    void invokeShouldRefresh();
}