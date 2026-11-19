package com.creeperyang.contactquests.mixin;

import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露 FTB 任务文件根对象的刷新索引表方法，供脚本图谱修改后重建查找索引。 */
@Mixin(BaseQuestFile.class)
public interface QuestFileAccessor {
    @Invoker(value = "refreshIDMap", remap = false)
    void contactQuests$refreshIdMap();
}
