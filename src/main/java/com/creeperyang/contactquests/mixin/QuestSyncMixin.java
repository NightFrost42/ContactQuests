package com.creeperyang.contactquests.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;

/** 在单机整合服务端共用静态 TaskType ID 表期间，对全量网络读写加锁防止序列化竞态。 */
@Mixin(BaseQuestFile.class)
public abstract class QuestSyncMixin {
    @WrapMethod(method = "writeNetDataFull", remap = false)
    private void guardFullWrite(RegistryFriendlyByteBuf buffer, Operation<Void> original) {
        synchronized (TaskType.class) {
            original.call(buffer);
        }
    }

    @WrapMethod(method = "readNetDataFull", remap = false)
    private void guardFullRead(RegistryFriendlyByteBuf buffer, Operation<Void> original) {
        synchronized (TaskType.class) {
            // readNetDataFull 会先清空所有静态类型 ID 再重建，服务端绝不能在这个空窗期内序列化任务图谱。
            original.call(buffer);
        }
    }
}
