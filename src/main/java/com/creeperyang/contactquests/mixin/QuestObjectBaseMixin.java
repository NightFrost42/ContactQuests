package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.utils.ClientLocaleHelper;
import com.creeperyang.contactquests.utils.IQuestObjectBaseExtension;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(QuestObjectBase.class)
public class QuestObjectBaseMixin implements IQuestObjectBaseExtension {
    @Unique
    private final Map<String, String> contactQuests$titleOverrides = new HashMap<>();

    @Unique
    public void contactQuests$setTitleOverride(String locale, String title) {
        if (title == null) {
            contactQuests$titleOverrides.remove(locale);
        } else {
            contactQuests$titleOverrides.put(locale, title);
        }
    }

    @Inject(method = "getRawTitle", at = @At("HEAD"), cancellable = true, remap = false)
    private void injectGetRawTitle(CallbackInfoReturnable<String> cir) {
        String locale = ClientLocaleHelper.getLocale();
        if (contactQuests$titleOverrides.containsKey(locale)) {
            cir.setReturnValue(contactQuests$titleOverrides.get(locale));
        }
    }
}
