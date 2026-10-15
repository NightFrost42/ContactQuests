package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.ContactQuests;
import com.creeperyang.contactquests.data.DataManager;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerQuestFile.class)
public class ServerQuestFileMixin {

    @Inject(method = "markDirty", at = @At("HEAD"), remap = false)
    private void contactQuests$markTargetsDirty(CallbackInfo ci) {
        DataManager.INSTANCE.targetsDirty = true;
    }

    @Inject(method = "saveNow", at = @At("TAIL"), remap = false)
    private void contactQuests$refreshTargets(CallbackInfo ci) {
        if (ServerQuestFile.INSTANCE == null) {
            return;
        }
        try {
            DataManager.INSTANCE.init();
        } catch (Exception e) {
            // 这里是 FTB 的存档路径，绝不能让异常冒出去影响任务文件保存
            ContactQuests.error("保存任务文件后重建投递目标失败: %s", e.toString());
        }
    }
}
