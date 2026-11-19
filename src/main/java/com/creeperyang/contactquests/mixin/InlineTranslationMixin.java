package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.quest.InlineQuestSettings;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.translation.TranslationManager;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TranslationManager.class)
public abstract class InlineTranslationMixin {
    @Inject(method = "processInitialTranslation", at = @At("HEAD"), cancellable = true, remap = false)
    private void keepInlineTranslationLocal(CompoundTag data, QuestObjectBase object, CallbackInfo ci) {
        if (InlineQuestSettings.isInline(object)) {
            var translated = data.getCompound("translate");
            if (translated.contains("title", 8)) object.setRawTitle(translated.getString("title"));
            ci.cancel();
        }
    }
}
