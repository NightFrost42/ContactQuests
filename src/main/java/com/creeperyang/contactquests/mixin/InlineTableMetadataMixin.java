package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.bridge.IInlineQuestTextOwner;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.loot.RewardTable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 为任务内嵌奖励表（fakeId = -1）打上本地标题标记并序列化所属元数据。 */
@Mixin(RewardTable.class)
public abstract class InlineTableMetadataMixin {
    @Shadow(remap = false) private String filename;

    @Inject(method = "<init>(JLdev/ftb/mods/ftbquests/quest/BaseQuestFile;Ljava/lang/String;)V", at = @At("TAIL"), remap = false)
    private void markEmbeddedQuest(long id, BaseQuestFile file, String filename, CallbackInfo ci) {
        if (id == -1L) ((IInlineQuestTextOwner) (Object) ((RewardTable) (Object) this).getFakeQuest()).contactQuests$setLocalInlineText(true);
    }

    @Inject(method = "readData", at = @At("TAIL"), remap = false)
    private void readInlineFilename(CompoundTag data, HolderLookup.Provider lookup, CallbackInfo ci) {
        if (((RewardTable) (Object) this).id == -1L && data.contains("filename", 8))
            filename = RewardTable.sanitizeFilename(data.getString("filename")).orElse("");
    }

    @Inject(method = "writeData", at = @At("TAIL"), remap = false)
    private void writeInlineFilename(CompoundTag data, HolderLookup.Provider lookup, CallbackInfo ci) {
        if (((RewardTable) (Object) this).id == -1L && !filename.isEmpty()) data.putString("filename", filename);
    }
}
