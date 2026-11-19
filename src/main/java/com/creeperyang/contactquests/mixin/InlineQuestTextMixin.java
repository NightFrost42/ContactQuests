package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.quest.InlineQuestSettings;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.translation.TranslationKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;

/** 为内嵌文本对象启用对象本地标题存储，避免共享伪造 ID -1 的奖励表引发跨对象或离线图谱污染。 */
@Mixin(QuestObjectBase.class)
public abstract class InlineQuestTextMixin {
    @Shadow(remap = false) protected EnumMap<TranslationKey, String> protoTranslations;
    @Shadow(remap = false) public abstract void clearCachedData();

    @Unique
    private boolean contactQuests$isInline() {
        return InlineQuestSettings.isInline((QuestObjectBase) (Object) this);
    }

    @Inject(method = "getRawTitle", at = @At("HEAD"), cancellable = true, remap = false)
    private void inlineTitle(CallbackInfoReturnable<String> cir) {
        if (contactQuests$isInline()) cir.setReturnValue(protoTranslations.getOrDefault(TranslationKey.TITLE, ""));
    }

    @Inject(method = "setRawTitle", at = @At("HEAD"), cancellable = true, remap = false)
    private void setInlineTitle(String title, CallbackInfo ci) {
        if (contactQuests$isInline()) {
            protoTranslations.put(TranslationKey.TITLE, title);
            clearCachedData();
            ci.cancel();
        }
    }

    @Inject(method = "readData", at = @At("TAIL"), remap = false)
    private void readInlineTitle(CompoundTag data, HolderLookup.Provider lookup, CallbackInfo ci) {
        if (contactQuests$isInline() && data.contains("title", 8)) {
            protoTranslations.put(TranslationKey.TITLE, data.getString("title"));
            clearCachedData();
        }
    }

    @Inject(method = "writeData", at = @At("TAIL"), remap = false)
    private void writeInlineTitle(CompoundTag data, HolderLookup.Provider lookup, CallbackInfo ci) {
        if (contactQuests$isInline() && protoTranslations.containsKey(TranslationKey.TITLE))
            data.putString("title", protoTranslations.get(TranslationKey.TITLE));
    }

    @Inject(method = "writeNetData", at = @At("TAIL"), remap = false)
    private void writeInlineTitleNet(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
        if (contactQuests$isInline()) buffer.writeUtf(protoTranslations.getOrDefault(TranslationKey.TITLE, ""));
    }

    @Inject(method = "readNetData", at = @At("TAIL"), remap = false)
    private void readInlineTitleNet(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
        if (contactQuests$isInline()) protoTranslations.put(TranslationKey.TITLE, buffer.readUtf());
    }
}
