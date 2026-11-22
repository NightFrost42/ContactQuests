package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.ContactQuests;
import com.creeperyang.contactquests.data.DataManager;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerQuestFile.class)
public abstract class ServerQuestFileMixin {
    @Inject(method = "markDirty", at = @At("HEAD"), remap = false)
    private void contactQuests$markTargetsDirty(CallbackInfo ci) {
        DataManager.targetsDirty = true;
    }

    @Inject(method = "saveNow", at = @At("TAIL"), remap = false)
    private void contactQuests$refreshTargets(CallbackInfo ci) {
        if (ServerQuestFile.INSTANCE != null) {
            try {
                DataManager.INSTANCE.ensureTargetsFresh();
            } catch (Exception exception) {
                ContactQuests.error("Failed to refresh ContactQuests addressees after saving: %s", exception);
            }
        }
    }
}
